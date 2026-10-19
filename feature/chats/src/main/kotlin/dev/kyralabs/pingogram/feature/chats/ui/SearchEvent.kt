package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.common.result.DomainError

/** What the user does on the search screen, sent to [SearchViewModel.onEvent]. */
sealed interface SearchEvent {
    /** The search field now holds [query]; it is searched once typing pauses. */
    data class QueryChanged(
        val query: String,
    ) : SearchEvent

    /** The clear button emptied the search field: the suggestions show at once. */
    data object ClearQuery : SearchEvent

    /** A chat, contact, public chat, recent chat or person was tapped: it opens, and joins the recent chats. */
    data class OpenChat(
        val chatId: Long,
    ) : SearchEvent

    /** A message result was tapped: its conversation opens at it. */
    data class OpenMessage(
        val chatId: Long,
        val messageId: Long,
    ) : SearchEvent

    /** Takes chat [chatId] out of the recent chats. */
    data class RemoveRecent(
        val chatId: Long,
    ) : SearchEvent

    /** Empties the recent chats. */
    data object ClearRecents : SearchEvent

    /** The list shows its end, or nearly: the next page of messages loads, if there is one and none is loading. */
    data object LoadMoreMessages : SearchEvent

    /** "Try again": every section that failed, and the page of messages that failed, search again. */
    data object Retry : SearchEvent
}

/** One-off effects of [SearchViewModel], handled by the screen once each (`docs/ARCHITECTURE.md`, ViewModels). */
sealed interface SearchEffect {
    /** Open chat [chatId]. */
    data class OpenChat(
        val chatId: Long,
    ) : SearchEffect

    /** Open the conversation of chat [chatId] at message [messageId]. */
    data class OpenMessage(
        val chatId: Long,
        val messageId: Long,
    ) : SearchEffect

    /** Changing the recent chats failed: a snackbar shows the error's text. */
    data class ShowError(
        val error: DomainError,
    ) : SearchEffect
}
