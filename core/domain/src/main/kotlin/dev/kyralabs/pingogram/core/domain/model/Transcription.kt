package dev.kyralabs.pingogram.core.domain.model

/**
 * The text of a voice or round video message, recognized on the device (`docs/DESIGN_SYSTEM.md` §6 "Voice message
 * transcription", the owner's board 28).
 *
 * @property text what the recognizer heard, as it wrote it; may be empty where it heard no words.
 * @property isAutomatic whether it was made by "Transcribe voice messages automatically", not by the user's tap.
 * @property isFirst whether it was the first transcript made on this device, which says "On this phone".
 */
data class Transcript(
    val text: String,
    val isAutomatic: Boolean = false,
    val isFirst: Boolean = false,
)

/** What came of transcribing a message on the device. */
sealed interface TranscriptionResult {
    /** The recognizer wrote [text]. */
    data class Recognized(
        val text: String,
    ) : TranscriptionResult

    /** The device has no speech language ready for it: Android downloads and manages them in its settings. */
    data object LanguageUnavailable : TranscriptionResult

    /**
     * Android lets apps use its speech recognizer only while they hold the microphone permission, also for audio they
     * hand it themselves; it is not granted. Pingogram records nothing for it.
     */
    data object NeedsMicrophonePermission : TranscriptionResult

    /** The file could not be read, or the recognizer failed. */
    data object Failed : TranscriptionResult
}

/**
 * The speech languages of the device's own recognizer (IETF BCP 47 tags): [ready] to use, [downloading] (Android
 * scheduled them), and [notDownloaded], which Android can download in its settings. Android manages all of them;
 * Pingogram bundles and downloads none.
 */
data class TranscriptionLanguages(
    val ready: List<String> = emptyList(),
    val downloading: List<String> = emptyList(),
    val notDownloaded: List<String> = emptyList(),
)

/**
 * The settings of voice message transcription, kept on this device: whether others' voice and round video messages are
 * transcribed as they show ([automatic], off by default), and whether a transcript said "On this phone" yet
 * ([noticeShown]).
 */
data class TranscriptionSettings(
    val automatic: Boolean = false,
    val noticeShown: Boolean = false,
)
