package dev.kyralabs.pingogram.core.data.transcription

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.domain.model.TranscriptionLanguages
import dev.kyralabs.pingogram.core.domain.model.TranscriptionResult
import dev.kyralabs.pingogram.core.domain.repository.VoiceTranscriber
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * [VoiceTranscriber] on Android's own on-device speech recognizer (`SpeechRecognizer.createOnDeviceSpeechRecognizer`,
 * Android 13 and later, the owner's board 28). Pingogram bundles no model and sends nothing anywhere: the recognizer
 * runs on the device (Android System Intelligence on Pixels), and Android downloads and manages its languages.
 *
 * - **Audio**: the message's file is decoded on the [io] dispatcher ([SpeechAudioDecoder]) and written into a pipe as
 *   16-bit mono PCM at 16 kHz, the read end of which the recognizer reads (`RecognizerIntent.EXTRA_AUDIO_SOURCE`) in
 *   a segmented session (`EXTRA_SEGMENTED_SESSION`), so it does not stop at the first pause: the text is every
 *   segment's, joined. The microphone is never opened.
 * - **Permission**: Android's recognition service checks that its caller holds `RECORD_AUDIO` before it delivers
 *   results, also for audio the caller hands it (AOSP `RecognitionService.checkPermissionAndStartDataDelivery`), unless
 *   the recognizer itself waives it. Pingogram already holds it for voice messages; without the grant,
 *   [TranscriptionResult.NeedsMicrophonePermission], and the recognizer is not asked. Android may show its microphone
 *   indicator while the recognizer works.
 * - **Language**: the first of Pingogram's and the system's languages whose speech language is ready
 *   (`checkRecognitionSupport`), else the first one ready; [TranscriptionResult.LanguageUnavailable] when none is, or
 *   when the recognizer says the language is not available (`ERROR_LANGUAGE_NOT_SUPPORTED`,
 *   `ERROR_LANGUAGE_UNAVAILABLE`).
 * - **Threads**: the recognizer is made, used and destroyed on the main thread, as it asks; it waits at most twice the
 *   audio's length, and at least [MIN_TIMEOUT_MILLIS]. One recognizer checks the languages and transcribes: destroying
 *   one unbinds the recognition service, which cancels a session another recognizer has just started.
 *
 * Neither the audio nor the text is logged.
 */
@Singleton
class AndroidVoiceTranscriber internal constructor(
    private val context: Context,
    private val io: CoroutineDispatcher,
    private val decoder: SpeechAudioDecoder,
) : VoiceTranscriber {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @Dispatcher(IO) io: CoroutineDispatcher,
    ) : this(context, io, MediaSpeechAudioDecoder)

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    override val isAvailable: Boolean
        get() =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override suspend fun transcribe(
        path: String,
        durationSeconds: Int,
    ): TranscriptionResult =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !isAvailable) {
            TranscriptionResult.Failed
        } else if (!holdsMicrophonePermission()) {
            TranscriptionResult.NeedsMicrophonePermission
        } else {
            transcribeOnDevice(path, durationSeconds)
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun transcribeOnDevice(
        path: String,
        durationSeconds: Int,
    ): TranscriptionResult =
        withRecognizer { recognizer ->
            val ready = withTimeoutOrNull(SUPPORT_TIMEOUT_MILLIS) { checkSupport(recognizer) }?.ready.orEmpty()
            val language = languageFor(ready, preferredLanguages())
            if (language == null) {
                TranscriptionResult.LanguageUnavailable
            } else {
                val timeout = maxOf(MIN_TIMEOUT_MILLIS, durationSeconds * MILLIS_PER_SECOND * 2L)
                withTimeoutOrNull(timeout) { recognize(recognizer, path, language) } ?: TranscriptionResult.Failed
            }
        }

    override suspend fun languages(): TranscriptionLanguages? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isAvailable) {
            withTimeoutOrNull(SUPPORT_TIMEOUT_MILLIS) { withRecognizer(::checkSupport) }
        } else {
            null
        }

    /** Runs [block] on the main thread with an on-device recognizer, destroyed after it. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun <T> withRecognizer(block: suspend (SpeechRecognizer) -> T): T =
        withContext(Dispatchers.Main) {
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            try {
                block(recognizer)
            } finally {
                recognizer.destroy()
            }
        }

    private fun holdsMicrophonePermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Pingogram's language first, then the system's, as BCP 47 tags. */
    private fun preferredLanguages(): List<String> {
        val locales = Resources.getSystem().configuration.locales
        val system = (0 until locales.size()).map { locales[it].toLanguageTag() }
        return (listOf(Locale.getDefault().toLanguageTag()) + system).distinct()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun checkSupport(recognizer: SpeechRecognizer): TranscriptionLanguages? =
        suspendCancellableCoroutine { continuation ->
            recognizer.checkRecognitionSupport(
                recognitionIntent(language = null),
                MainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        continuation.resume(
                            TranscriptionLanguages(
                                ready = support.installedOnDeviceLanguages.toImmutableList(),
                                downloading = support.pendingOnDeviceLanguages.toImmutableList(),
                                notDownloaded =
                                    (
                                        support.supportedOnDeviceLanguages -
                                            support.installedOnDeviceLanguages.toSet() -
                                            support.pendingOnDeviceLanguages.toSet()
                                    ).toImmutableList(),
                            ),
                        )
                    }

                    override fun onError(error: Int) {
                        continuation.resume(null)
                    }
                },
            )
        }

    /** Decodes [path] into a pipe while [recognizer], on the main thread, reads it in [language]. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun recognize(
        recognizer: SpeechRecognizer,
        path: String,
        language: String,
    ): TranscriptionResult =
        coroutineScope {
            val (source, sink) = ParcelFileDescriptor.createPipe()
            val writer =
                launch(io) { decoder.decode(path, ParcelFileDescriptor.AutoCloseOutputStream(sink)) }
            try {
                listen(recognizer, source, language)
            } finally {
                // Closing the read end ends a write still waiting for the recognizer to read.
                source.close()
                writer.cancel()
            }
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun listen(
        recognizer: SpeechRecognizer,
        source: ParcelFileDescriptor,
        language: String,
    ): TranscriptionResult =
        suspendCancellableCoroutine { continuation ->
            val segments = mutableListOf<String>()

            fun finish(result: TranscriptionResult) {
                if (continuation.isActive) continuation.resume(result)
            }

            recognizer.setRecognitionListener(
                object : RecognitionListener {
                    override fun onSegmentResults(segmentResults: Bundle) {
                        segmentResults.bestResult()?.let(segments::add)
                    }

                    override fun onEndOfSegmentedSession() {
                        finish(TranscriptionResult.Recognized(segments.joinTranscript()))
                    }

                    override fun onResults(results: Bundle) {
                        results.bestResult()?.let(segments::add)
                        finish(TranscriptionResult.Recognized(segments.joinTranscript()))
                    }

                    override fun onError(error: Int) {
                        finish(resultOf(error, segments))
                    }

                    override fun onPartialResults(partialResults: Bundle?) = Unit

                    override fun onReadyForSpeech(params: Bundle?) = Unit

                    override fun onBeginningOfSpeech() = Unit

                    override fun onRmsChanged(rmsdB: Float) = Unit

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() = Unit

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?,
                    ) = Unit
                },
            )
            recognizer.startListening(recognitionIntent(language, source))
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun recognitionIntent(
        language: String?,
        source: ParcelFileDescriptor? = null,
    ): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (language != null) putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            if (source != null) {
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, source)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SpeechPcm.OUTPUT_RATE)
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Punctuation and capitals, where the recognizer can.
                putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
            }
        }

    private companion object {
        /** The least the recognizer is given, for messages a few seconds long. */
        const val MIN_TIMEOUT_MILLIS = 30_000L

        /** How long the recognizer may take to list its languages. */
        const val SUPPORT_TIMEOUT_MILLIS = 10_000L

        const val MILLIS_PER_SECOND = 1_000L
    }
}

/** Runs the recognizer's callbacks where they come, the main thread. */
private object MainExecutor : Executor {
    override fun execute(command: Runnable) = command.run()
}

/** The recognizer's best guess in [this] results, if it has one. */
private fun Bundle.bestResult(): String? =
    getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

private fun List<String>.joinTranscript(): String = joinToString(separator = " ")

/** What the recognizer's [error] means, keeping the [segments] it wrote before it. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun resultOf(
    error: Int,
    segments: List<String>,
): TranscriptionResult =
    when {
        segments.isNotEmpty() -> {
            TranscriptionResult.Recognized(segments.joinTranscript())
        }

        // It heard no words: an empty transcript, which the bubble says.
        error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
            TranscriptionResult.Recognized("")
        }

        // A speech language it does not have, or has not downloaded.
        error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
            error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
            TranscriptionResult.LanguageUnavailable
        }

        error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
            TranscriptionResult.NeedsMicrophonePermission
        }

        else -> {
            TranscriptionResult.Failed
        }
    }

/**
 * The speech language to transcribe in: the first of [preferred] (BCP 47 tags) that a [ready] language has, by the
 * whole tag, then by the language alone ("en-IN" takes "en-US"); else the first ready one; null when none is.
 */
internal fun languageFor(
    ready: List<String>,
    preferred: List<String>,
): String? {
    if (ready.isEmpty()) return null

    fun base(tag: String) = Locale.forLanguageTag(tag).language
    return preferred.firstNotNullOfOrNull { tag -> ready.firstOrNull { it.equals(tag, ignoreCase = true) } }
        ?: preferred.firstNotNullOfOrNull { tag -> ready.firstOrNull { base(it) == base(tag) } }
        ?: ready.first()
}
