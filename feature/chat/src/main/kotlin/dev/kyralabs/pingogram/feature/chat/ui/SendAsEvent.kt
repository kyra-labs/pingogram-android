package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.MessageSender

/** What the user does with send-as (`REQUIREMENTS.md` FR-C5), sent to [ChatViewModel.onEvent]. */
sealed interface SendAsEvent : ChatEvent {
    /** The composer's send-as avatar: the picker opens and asks Telegram who the user can send as. */
    data object Open : SendAsEvent

    /** A sender tapped in the picker: new messages go out as [sender], and the picker closes once TDLib accepted it. */
    data class Choose(
        val sender: MessageSender,
    ) : SendAsEvent

    /** The picker was dismissed: nothing changes. */
    data object Dismiss : SendAsEvent
}
