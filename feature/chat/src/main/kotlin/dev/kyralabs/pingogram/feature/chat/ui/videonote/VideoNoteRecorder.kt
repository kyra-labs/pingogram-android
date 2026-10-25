package dev.kyralabs.pingogram.feature.chat.ui.videonote

import androidx.camera.core.SurfaceRequest
import dev.kyralabs.pingogram.core.domain.model.AttachmentThumbnail
import kotlinx.coroutines.flow.StateFlow

/**
 * Records round video messages from the camera and the microphone into files, one at a time
 * (`docs/adr/0032-round-video-messages.md`): the platform side of a conversation's video recording (`ChatRecorder`),
 * behind an interface so tests use a fake, as `VoiceRecorder` is. [CameraXVideoNoteRecorder] in the app. Called from
 * the main thread; the calls that wait for the camera or the encoder suspend, so none blocks a frame. Public only
 * because `ChatViewModel`'s injected dependencies take it.
 *
 * The camera runs only between [start] and the end of the recording ([stop], [cancel], [release]), which release the
 * camera and the microphone at once.
 */
interface VideoNoteRecorder {
    /** Whether the device has a camera at all; without one, the record button only records voice. */
    val hasCamera: Boolean

    /**
     * What the round preview draws while the camera runs: CameraX's request for the viewfinder's surface, already
     * cropped to the recording's square; null while it does not run.
     */
    val preview: StateFlow<SurfaceRequest?>

    /**
     * Opens the [front] camera (or the other one where there is only one) and the microphone, and starts recording a
     * square video into the empty file at [path]. Null when they cannot be used (another app has the camera, a
     * permission is missing): nothing is recorded then.
     */
    suspend fun start(
        path: String,
        front: Boolean,
    ): VideoNoteStart?

    /**
     * Records from the [front] camera from now on, the recording going on in the same file; false when that camera
     * cannot be used, and the recording goes on from the one before.
     */
    suspend fun flip(front: Boolean): Boolean

    /** Pauses the recording; the file goes on where it stopped on [resume]. The camera keeps running meanwhile. */
    suspend fun pause()

    suspend fun resume()

    /** How loud the microphone was lately, from 0 (silent) to 1; 0 while not recording. */
    suspend fun level(): Float

    /**
     * Ends the recording, finishes its file and writes the still of its first frame to [thumbnail], releasing the
     * camera and the microphone. Null when the file holds no video (too short, or it failed).
     */
    suspend fun stop(thumbnail: String): RecordedVideo?

    /** Ends the recording and gives up its file, which the caller deletes; returns at once. Nothing if none runs. */
    fun cancel()

    /** Frees the recorder for good, ending a recording as [cancel] does. */
    fun release()
}

/**
 * How a recording started: whether the device has a second camera to [flip][VideoNoteRecorder.flip] to ([canFlip]),
 * and which camera it records from ([front]).
 */
data class VideoNoteStart(
    val front: Boolean,
    val canFlip: Boolean,
)

/** A finished recording: the side of its square in pixels ([length]) and its first frame's still, if one was made. */
data class RecordedVideo(
    val length: Int,
    val thumbnail: AttachmentThumbnail?,
)
