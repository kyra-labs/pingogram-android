package dev.kyralabs.pingogram.feature.chat.ui

/** What the user does with voice message transcription (`docs/DESIGN_SYSTEM.md` §6), sent to [ChatViewModel]. */
sealed interface TranscriptionEvent : ChatEvent {
    /** The bubble's Transcribe button, its custom action, or "Try again": the message's text shows, made if needed. */
    data class Transcribe(
        val messageId: Long,
    ) : TranscriptionEvent

    /** "Hide text" (the button again, or the text button): the bubble shows its audio alone. */
    data class Hide(
        val messageId: Long,
    ) : TranscriptionEvent

    /** "Show more" under a long transcript: it shows whole. */
    data class ShowMore(
        val messageId: Long,
    ) : TranscriptionEvent

    /** "Android language settings": Android's page of speech languages opens. */
    data object OpenLanguageSettings : TranscriptionEvent

    /** Android answered the request for the microphone permission that its recognizer needs. */
    data class PermissionAnswered(
        val granted: Boolean,
    ) : TranscriptionEvent
}

/** One-off effects of voice message transcription. */
sealed interface TranscriptionEffect : ChatEffect {
    /**
     * Android lets its recognizer work only for apps with the microphone permission: a snackbar says so, with "Allow",
     * which asks for it (answered by [TranscriptionEvent.PermissionAnswered]).
     */
    data object ExplainMicrophone : TranscriptionEffect

    /** The permission was refused: a snackbar with "Settings", the app's page in Android's settings. */
    data object MicrophoneDenied : TranscriptionEffect

    /** Opens Android's speech language settings. */
    data object OpenLanguageSettings : TranscriptionEffect
}
