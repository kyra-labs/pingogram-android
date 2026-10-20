package dev.kyralabs.pingogram.feature.chat.ui

/**
 * What the user does with the composer's voice recording (`REQUIREMENTS.md` FR-C5), sent to [ChatViewModel.onEvent].
 * Holding the microphone records ([Start]); sliding toward the start cancels ([Cancel]), sliding up locks ([Lock]),
 * releasing sends ([Send]). Locked, it records hands-free until Send or Delete ([Cancel]), and can pause.
 */
sealed interface VoiceEvent : ChatEvent {
    /**
     * Start recording, the microphone permission being granted: held ([locked] false) while the finger stays on the
     * button, or [locked] at once when the button is activated without touch (a screen reader, a keyboard).
     */
    data class Start(
        val locked: Boolean = false,
    ) : VoiceEvent

    /** The microphone was pressed without the permission: a snackbar explains why it is needed, offering to ask. */
    data object PermissionNeeded : VoiceEvent

    /** The system's permission request was answered: [granted], or refused (a snackbar then offers the settings). */
    data class PermissionAnswered(
        val granted: Boolean,
    ) : VoiceEvent

    /** Slid up while holding: the recording goes on without the finger. */
    data object Lock : VoiceEvent

    data object Pause : VoiceEvent

    data object Resume : VoiceEvent

    /** Released while holding, or the send button while locked: the voice message is sent, unless it is too short. */
    data object Send : VoiceEvent

    /** Slid toward the start while holding, or Delete while locked: the recording is thrown away. */
    data object Cancel : VoiceEvent
}
