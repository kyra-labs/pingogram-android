package dev.kyralabs.pingogram.feature.chat.ui.interactions

import dev.kyralabs.pingogram.core.common.result.DomainError
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

    /**
     * "Remove reaction" ([all] false) or "Remove all recent reactions" ([all]) on [sender]'s row: the question before
     * it, as administrators remove others' reactions.
     */
    data class AskRemove(
        val sender: MessageSender,
        val all: Boolean,
    ) : InteractionsEvent

    /** "Remove" in the question. */
    data object ConfirmRemove : InteractionsEvent

    /** "Cancel" in the question, or dismissing it. */
    data object DismissRemove : InteractionsEvent
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

    /** Someone's reactions were removed: from the message, or [all] their recent ones. A snackbar. */
    data class ReactionsRemoved(
        val all: Boolean,
    ) : InteractionsEffect

    /** Telegram did not remove them, because of [error]. A snackbar. */
    data class RemovalFailed(
        val error: DomainError,
    ) : InteractionsEffect
}
