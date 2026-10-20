package dev.kyralabs.pingogram.feature.chat.ui.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * [VoiceRecorder] on the platform's [MediaRecorder] (`docs/adr/0005-voice-recording.md`), no library: Ogg Opus at
 * 48 kHz, mono, 32 kbit/s where the device has an Opus encoder (Android 10 and later), else AAC at 44.1 kHz, mono,
 * 64 kbit/s in an `.m4a` file.
 *
 * The recorder lives on a thread of its own, started on the first call, so a conversation nobody records in costs
 * nothing: preparing, starting and above all stopping a [MediaRecorder] wait for the audio input and the encoder, for
 * up to a few hundred milliseconds, which on the main thread would hold up frames. Every call runs there in the order
 * it was made; [cancel] and [release] are posted and return at once.
 *
 * While it records it holds the audio focus as Android asks of voice memo apps (`AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`),
 * so music and other apps' speech pause rather than end up in the message.
 */
@Suppress("TooManyFunctions") // One override per VoiceRecorder call, and its thread's and the audio focus's helpers.
internal class MediaVoiceRecorder
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : VoiceRecorder {
        // Main thread.

        /** The recorder's thread; null until the first call, and after [release]. */
        private var executor: ExecutorService? = null
        private var dispatcher: CoroutineDispatcher? = null

        // The recorder's thread.

        private var recorder: MediaRecorder? = null
        private var focusRequest: AudioFocusRequest? = null
        private var detectedFormat: VoiceFormat? = null

        private val audioManager: AudioManager? get() = context.getSystemService(AudioManager::class.java)

        override suspend fun format(): VoiceFormat =
            onRecorderThread { detectedFormat ?: detectFormat().also { detectedFormat = it } }

        override suspend fun start(
            path: String,
            format: VoiceFormat,
        ): Boolean =
            onRecorderThread {
                releaseRecorder()
                val recorder = newMediaRecorder()
                try {
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                    // detectFormat() never picks Ogg Opus before Android 10.
                    if (format == VoiceFormat.OggOpus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        recorder.useOggOpus()
                    } else {
                        recorder.useAac()
                    }
                    recorder.setAudioChannels(1)
                    recorder.setOutputFile(path)
                    recorder.prepare()
                    requestFocus()
                    recorder.start()
                    this.recorder = recorder
                    true
                } catch (_: IOException) {
                    recorder.release()
                    abandonFocus()
                    false
                } catch (_: RuntimeException) {
                    // IllegalStateException, or a RuntimeException when the microphone is taken or not allowed.
                    recorder.release()
                    abandonFocus()
                    false
                }
            }

        override suspend fun pause() {
            onRecorderThread {
                try {
                    recorder?.pause()
                } catch (_: IllegalStateException) {
                    // Not recording: nothing to pause.
                }
            }
        }

        override suspend fun resume() {
            onRecorderThread {
                try {
                    recorder?.resume()
                } catch (_: IllegalStateException) {
                    // Not paused: nothing to resume.
                }
            }
        }

        override suspend fun level(): Float =
            onRecorderThread {
                val amplitude =
                    try {
                        recorder?.maxAmplitude ?: 0
                    } catch (_: IllegalStateException) {
                        0
                    }
                amplitudeLevel(amplitude)
            }

        override suspend fun stop(): Boolean =
            onRecorderThread {
                val recorder = recorder ?: return@onRecorderThread false
                this.recorder = null
                try {
                    recorder.stop()
                    true
                } catch (_: RuntimeException) {
                    // Stopped before any audio was encoded: the file is unusable.
                    false
                } finally {
                    recorder.release()
                    abandonFocus()
                }
            }

        override fun cancel() {
            executor?.execute(::releaseRecorder)
        }

        override fun release() {
            val executor = executor ?: return
            this.executor = null
            dispatcher = null
            executor.execute(::releaseRecorder)
            // Runs what was posted before, then ends the thread.
            executor.shutdown()
        }

        /** Runs [block] on the recorder's thread, starting it on the first call. */
        private suspend fun <T> onRecorderThread(block: () -> T): T {
            val dispatcher =
                dispatcher ?: Executors
                    .newSingleThreadExecutor { runnable -> Thread(runnable, THREAD_NAME) }
                    .also { executor = it }
                    .asCoroutineDispatcher()
                    .also { dispatcher = it }
            return withContext(dispatcher) { block() }
        }

        /** Ends and frees the recording in progress, if any, without keeping its file. */
        private fun releaseRecorder() {
            val recorder = recorder ?: return
            this.recorder = null
            try {
                recorder.stop()
            } catch (_: RuntimeException) {
                // Nothing encoded yet; the file is given up anyway.
            }
            recorder.release()
            abandonFocus()
        }

        private fun requestFocus() {
            val request =
                AudioFocusRequest
                    .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    ).build()
            // Recording goes on without the focus: it only keeps other apps quiet.
            audioManager?.requestAudioFocus(request)
            focusRequest = request
        }

        private fun abandonFocus() {
            val request = focusRequest ?: return
            focusRequest = null
            audioManager?.abandonAudioFocusRequest(request)
        }

        private fun newMediaRecorder(): MediaRecorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION") // The constructor that takes a context is Android 12's.
                MediaRecorder()
            }

        private companion object {
            const val THREAD_NAME = "VoiceRecorder"
        }
    }

/** Ogg Opus where the platform has an Opus encoder (Android 10 and later), else AAC. */
private fun detectFormat(): VoiceFormat =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasOpusEncoder()) VoiceFormat.OggOpus else VoiceFormat.Aac

/** Whether the device has an encoder for mono Opus at [OPUS_SAMPLE_RATE]. */
private fun hasOpusEncoder(): Boolean =
    try {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, OPUS_SAMPLE_RATE, 1)
        MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format) != null
    } catch (_: RuntimeException) {
        false
    }

@RequiresApi(Build.VERSION_CODES.Q)
private fun MediaRecorder.useOggOpus() {
    setOutputFormat(MediaRecorder.OutputFormat.OGG)
    setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
    setAudioSamplingRate(OPUS_SAMPLE_RATE)
    setAudioEncodingBitRate(OPUS_BIT_RATE)
}

private fun MediaRecorder.useAac() {
    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
    setAudioSamplingRate(AAC_SAMPLE_RATE)
    setAudioEncodingBitRate(AAC_BIT_RATE)
}

/** Opus's own rate, which Telegram's apps record at too. */
private const val OPUS_SAMPLE_RATE = 48_000

/** Clear speech at about 240 KB a minute. */
private const val OPUS_BIT_RATE = 32_000

private const val AAC_SAMPLE_RATE = 44_100

/** AAC needs about twice Opus's rate for the same speech. */
private const val AAC_BIT_RATE = 64_000
