package dev.kyralabs.pingogram.feature.chat.ui

/** What the user does in a secret chat (`REQUIREMENTS.md` FR-E1), sent to [ChatViewModel.onEvent]. */
sealed interface SecretChatEvent : ChatEvent {
    /** The top bar's timer: the self-destruct timer sheet opens with the chat's timer chosen. */
    data object OpenTimer : SecretChatEvent

    /** A timer tapped in the sheet, in seconds (0 for off): it shows chosen, and the sheet closes once TDLib set it. */
    data class ChooseTimer(
        val seconds: Int,
    ) : SecretChatEvent

    /** The top bar's key: the encryption key sheet opens. */
    data object OpenKey : SecretChatEvent

    /** "End secret chat" in the key sheet: a confirmation asks first. */
    data object EndChat : SecretChatEvent

    /** The confirmation's "End chat": the secret chat ends for both sides, and the sheet closes. */
    data object ConfirmEnd : SecretChatEvent

    /** The confirmation was dismissed: the key sheet stays. */
    data object DismissEnd : SecretChatEvent

    /** The open sheet was dismissed: nothing changes; a timer already on its way to TDLib still goes. */
    data object Dismiss : SecretChatEvent
}
