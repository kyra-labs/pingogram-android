package dev.kyralabs.pingogram.feature.chat.ui.videonote

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.util.Range
import android.util.Rational
import android.view.Surface
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.ExperimentalAudioApi
import androidx.camera.video.ExperimentalPersistentRecording
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.domain.model.AttachmentThumbnail
import dev.kyralabs.pingogram.feature.chat.ui.voice.amplitudeLevel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * [VideoNoteRecorder] on CameraX (`docs/adr/0032-round-video-messages.md`): a [Preview] for the round preview and a
 * [VideoCapture] of a [Recorder] in one [UseCaseGroup] whose 1:1 [ViewPort] crops both to the same square, bound to a
 * lifecycle of its own ([RecordingLifecycle]) that is resumed only while it records, so the camera and the microphone
 * run from [start] to the end of the recording and no longer.
 *
 * What it records: SD quality (480p, or the closest the camera has) cropped to its square, so 480 × 480 on most
 * devices, H.264 at [VIDEO_BIT_RATE] and 30 fps, the front camera mirrored as its preview is, with the microphone's
 * AAC, into an MP4 file at most [VideoNoteLimits.MAX_DURATION_MILLIS] long. The recording is persistent, so a flip
 * binds the other camera without ending it. Once it ends, the file's first frame is written as a JPEG at most
 * [THUMBNAIL_SIDE] pixels on a side, on [io].
 *
 * Called on the main thread, where CameraX wants its bindings; its events come there too.
 */
@Suppress("TooManyFunctions") // One override per VideoNoteRecorder call, and the binding's and the file's helpers.
internal class CameraXVideoNoteRecorder
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:Dispatcher(IO) private val io: CoroutineDispatcher,
    ) : VideoNoteRecorder {
        private val lifecycle = RecordingLifecycle()
        private val surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
        private var provider: ProcessCameraProvider? = null

        /** The recording in progress; null while there is none. */
        private var take: Take? = null

        override val hasCamera: Boolean
            get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

        override val preview: StateFlow<SurfaceRequest?> = surfaceRequest.asStateFlow()

        @Suppress("ReturnCount") // Guard clauses: each thing the camera may lack ends it early.
        // Lint does not see the check of RECORD_AUDIO at the top of the function across its suspension points.
        @SuppressLint("MissingPermission")
        @androidx.annotation.OptIn(ExperimentalPersistentRecording::class)
        override suspend fun start(
            path: String,
            front: Boolean,
        ): VideoNoteStart? {
            cancel()
            // The screen asks for both before it records; checked again, as the microphone's grant can be taken back.
            val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PERMISSION_GRANTED
            if (!granted) return null
            val provider = cameraProvider() ?: return null
            val selector = provider.selectorFor(front) ?: return null
            val capture = videoCapture()
            val recorder = capture.output
            val preview =
                Preview.Builder().build().also {
                    it.setSurfaceProvider { request ->
                        surfaceRequest.value =
                            request
                    }
                }
            val group =
                UseCaseGroup
                    .Builder()
                    .setViewPort(ViewPort.Builder(SQUARE, Surface.ROTATION_0).build())
                    .addUseCase(preview)
                    .addUseCase(capture)
                    .build()
            val take = Take(path, provider, preview, capture, group, isFront = selector == FRONT)
            lifecycle.resume()
            val started =
                try {
                    provider.bindToLifecycle(lifecycle, selector, group)
                    val output = FileOutputOptions.Builder(File(path)).setDurationLimitMillis(LIMIT_MILLIS).build()
                    take.recording =
                        recorder
                            .prepareRecording(context, output)
                            .withAudioEnabled()
                            .asPersistentRecording()
                            .start(ContextCompat.getMainExecutor(context), take::onEvent)
                    true
                } catch (_: IllegalArgumentException) {
                    // No camera matches, or the use cases do not fit it.
                    false
                } catch (_: IllegalStateException) {
                    // The camera is closed or in use, or another recording runs.
                    false
                } catch (_: SecurityException) {
                    // The microphone's permission is missing.
                    false
                }
            if (!started) {
                unbind(take)
                return null
            }
            this.take = take
            return VideoNoteStart(front = take.isFront, canFlip = provider.hasBothCameras())
        }

        /** A [VideoCapture] of a new [Recorder] at the recording's quality, bit rate and frame rate. */
        private fun videoCapture(): VideoCapture<Recorder> {
            val recorder =
                Recorder
                    .Builder()
                    .setQualitySelector(QUALITY)
                    .setTargetVideoEncodingBitRate(VIDEO_BIT_RATE)
                    .build()
            return VideoCapture
                .Builder(recorder)
                .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
                .setTargetFrameRate(FRAME_RATE)
                .build()
        }

        @Suppress("ReturnCount") // Guard clauses: each thing the camera may lack ends it early.
        override suspend fun flip(front: Boolean): Boolean {
            val take = take ?: return false
            val selector = take.provider.selectorFor(front)?.takeIf { (it == FRONT) == front } ?: return false
            val before = if (take.isFront) FRONT else BACK
            take.provider.unbind(take.preview, take.capture)
            // The recording is persistent: it goes on in its file while the use cases move to the other camera.
            return try {
                take.provider.bindToLifecycle(lifecycle, selector, take.group)
                take.isFront = front
                true
            } catch (_: IllegalArgumentException) {
                rebind(take, before)
            } catch (_: IllegalStateException) {
                rebind(take, before)
            }
        }

        override suspend fun pause() {
            take?.recording?.pause()
        }

        override suspend fun resume() {
            take?.recording?.resume()
        }

        override suspend fun level(): Float = take?.level ?: 0f

        @Suppress("ReturnCount") // Guard clauses: each thing the camera may lack ends it early.
        override suspend fun stop(thumbnail: String): RecordedVideo? {
            val take = take ?: return null
            this.take = null
            take.recording?.stop()
            val finalized = withTimeoutOrNull(FINALIZE_TIMEOUT_MILLIS) { take.finalized.await() }
            unbind(take)
            if (finalized == null || !finalized.isUsable) return null
            return withContext(io) { readVideo(File(take.path), File(thumbnail)) }
        }

        override fun cancel() {
            val take = take ?: return
            this.take = null
            take.recording?.close()
            unbind(take)
        }

        override fun release() {
            cancel()
            lifecycle.destroy()
        }

        private suspend fun cameraProvider(): ProcessCameraProvider? =
            provider ?: try {
                ProcessCameraProvider.awaitInstance(context).also { provider = it }
            } catch (_: IllegalStateException) {
                // CameraX could not be initialized: no camera service, or none it can use.
                null
            }

        /** Binds [take]'s use cases to the camera it had before a failed flip; false either way. */
        private fun rebind(
            take: Take,
            selector: CameraSelector,
        ): Boolean {
            try {
                take.provider.bindToLifecycle(lifecycle, selector, take.group)
            } catch (_: IllegalArgumentException) {
                // That camera is gone too: the recording ends with no source, and its file is checked when it stops.
            } catch (_: IllegalStateException) {
                // As above.
            }
            return false
        }

        /** Frees the camera and the microphone of [take]: its use cases unbound, the lifecycle stopped, no preview. */
        private fun unbind(take: Take) {
            take.provider.unbind(take.preview, take.capture)
            take.preview.setSurfaceProvider(null)
            surfaceRequest.value = null
            lifecycle.stop()
        }

        /** The side of the recorded square, and its first frame written as a JPEG to [thumbnail], if it could be. */
        private fun readVideo(
            video: File,
            thumbnail: File,
        ): RecordedVideo? {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(video.path)
                val width = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val height = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                if (width <= 0 || height <= 0) return null
                val rotation = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                val still =
                    retriever
                        .getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { frame -> writeThumbnail(frame, rotation, thumbnail) }
                RecordedVideo(length = minOf(width, height), thumbnail = still)
            } catch (_: IllegalArgumentException) {
                // Not a video the platform can read.
                null
            } catch (_: RuntimeException) {
                // The retriever failed on the file.
                null
            } finally {
                retriever.release()
            }
        }

        private fun writeThumbnail(
            frame: Bitmap,
            rotation: Int,
            file: File,
        ): AttachmentThumbnail? {
            val scale = THUMBNAIL_SIDE.toFloat() / max(frame.width, frame.height)
            val matrix =
                Matrix().apply {
                    if (scale < 1f) setScale(scale, scale)
                    postRotate(rotation.toFloat())
                }
            val still = Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
            if (still !== frame) frame.recycle()
            return try {
                file.outputStream().use { still.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                AttachmentThumbnail(file.path, still.width, still.height)
            } catch (_: IOException) {
                null
            } finally {
                still.recycle()
            }
        }

        /** A recording: its file, its camera's use cases, the [Recording], and what its events said. */
        private inner class Take(
            val path: String,
            val provider: ProcessCameraProvider,
            val preview: Preview,
            val capture: VideoCapture<Recorder>,
            val group: UseCaseGroup,
            var isFront: Boolean,
        ) {
            var recording: Recording? = null
            val finalized = CompletableDeferred<VideoRecordEvent.Finalize>()

            /** The microphone's level from the last status event, 0 to 1. */
            var level = 0f

            @androidx.annotation.OptIn(ExperimentalAudioApi::class)
            fun onEvent(event: VideoRecordEvent) {
                when (event) {
                    is VideoRecordEvent.Status -> {
                        val amplitude = event.recordingStats.audioStats.audioAmplitude
                        level = amplitudeLevel((amplitude * MAX_AMPLITUDE).roundToInt())
                    }

                    is VideoRecordEvent.Finalize -> {
                        level = 0f
                        finalized.complete(event)
                    }

                    else -> {}
                }
            }
        }
    }

/**
 * A lifecycle that only this recorder moves: CameraX keeps the camera open while it is resumed. Main thread only.
 */
private class RecordingLifecycle : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry

    fun resume() {
        if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) registry.currentState = Lifecycle.State.CREATED
    }

    fun destroy() {
        if (registry.currentState != Lifecycle.State.INITIALIZED) registry.currentState = Lifecycle.State.DESTROYED
    }
}

/** Whether a finished recording's file can be sent: none failed, or it only reached its length's limit. */
private val VideoRecordEvent.Finalize.isUsable: Boolean
    get() = !hasError() || error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED

/** The [front] camera where there is one, else the other; null without a camera CameraX can use. */
private fun ProcessCameraProvider.selectorFor(front: Boolean): CameraSelector? {
    val preferred = if (front) FRONT else BACK
    val other = if (front) BACK else FRONT
    return listOf(preferred, other).firstOrNull { selector ->
        try {
            hasCamera(selector)
        } catch (_: CameraInfoUnavailableException) {
            false
        }
    }
}

private fun ProcessCameraProvider.hasBothCameras(): Boolean =
    try {
        hasCamera(FRONT) && hasCamera(BACK)
    } catch (_: CameraInfoUnavailableException) {
        false
    }

private fun MediaMetadataRetriever.metadataInt(key: Int): Int = extractMetadata(key)?.toIntOrNull() ?: 0

private val FRONT = CameraSelector.DEFAULT_FRONT_CAMERA
private val BACK = CameraSelector.DEFAULT_BACK_CAMERA

/** 480p, or the closest the camera records: about 480 × 480 once cropped to the square. */
private val QUALITY = QualitySelector.from(Quality.SD, FallbackStrategy.higherQualityOrLowerThan(Quality.SD))

/** The square both the preview and the video are cropped to. */
private val SQUARE = Rational(1, 1)

/** About 1 Mbit/s: at most about 7.5 MB of video in a minute. */
private const val VIDEO_BIT_RATE = 1_000_000

private val FRAME_RATE = Range(30, 30)

/** The recording's limit in the file itself, should the view model's own come late. */
private const val LIMIT_MILLIS = VideoNoteLimits.MAX_DURATION_MILLIS + 1_000L

/** How long finishing a file may take before it counts as failed. */
private const val FINALIZE_TIMEOUT_MILLIS = 5_000L

/** The longer side of a video's thumbnail, as Telegram's apps send them. */
private const val THUMBNAIL_SIDE = 320
private const val JPEG_QUALITY = 87

/** CameraX gives the amplitude from 0 to 1; [amplitudeLevel] takes the platform recorder's 0–32767. */
private const val MAX_AMPLITUDE = 32_767
