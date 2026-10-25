package dev.kyralabs.pingogram.feature.chat.ui.interactions

import dev.kyralabs.pingogram.core.domain.model.MessageSender

/**
 * What the user does in the sheet of who saw a message and who reacted to it, sent to [MessageInteractionsViewModel].
 */
sealed interface InteractionsEvent {
    /** A tab was tapped: it shows, loading its first page if it has none. */
    data class SelectTab(
        val tab: InteractionsTab,
    ) : InteractionsEvent

    /** The end of the selected tab's list came near: its next page loads, if it has one. */
    data object LoadMore : InteractionsEvent

    /** "Try again" after the selected tab's first or next page failed. */
    data object Retry : InteractionsEvent

    /** A row was tapped: the sheet closes, and [sender]'s profile, or its chat's info, opens. */
    data class Open(
        val sender: MessageSender,
    ) : InteractionsEvent

    /** Dragging the sheet down, the scrim or back. */
    data object Dismiss : InteractionsEvent
}

/** One-off effects of [MessageInteractionsViewModel], handled by the conversation's route once each. */
sealed interface InteractionsEffect {
    /** Open the profile of user [userId] (`REQUIREMENTS.md` FR-P2). */
    data class OpenProfile(
        val userId: Long,
    ) : InteractionsEffect

    /** Open the info page of chat [chatId], a channel someone reacted as. */
    data class OpenChat(
        val chatId: Long,
    ) : InteractionsEffect
}
