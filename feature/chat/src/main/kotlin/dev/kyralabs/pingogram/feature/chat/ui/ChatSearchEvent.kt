package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.MessageSender

/**
 * What the user does with the search in the conversation (`REQUIREMENTS.md` §5.9, in-chat search), sent to
 * [ChatViewModel]. The query is the search field's text, which the view model follows itself.
 */
sealed interface ChatSearchEvent : ChatEvent {
    /** The top bar's search button: the search field takes the top bar's place, its bar the composer's. */
    data object Open : ChatSearchEvent

    /** The field's back button, or back: search closes, with its query, sender and results. */
    data object Close : ChatSearchEvent

    /** The bar's up button: the next older result shows in the list, the next page loading past the last one. */
    data object Older : ChatSearchEvent

    /** The bar's down button: the next newer result shows in the list. */
    data object Newer : ChatSearchEvent

    /** The bar's "Try again" after a search failed: it searches again. */
    data object Retry : ChatSearchEvent

    /** The bar's sender button: the sheet of the chat's members opens, to search the messages of one of them. */
    data object OpenSenders : ChatSearchEvent

    /** The members' sheet was dismissed. */
    data object DismissSenders : ChatSearchEvent

    /** A member tapped in the sheet: the sheet closes, and search finds [sender]'s messages only. */
    data class PickSender(
        val sender: MessageSender,
    ) : ChatSearchEvent

    /** The sender chip's close button: search finds everyone's messages again. */
    data object ClearSender : ChatSearchEvent

    /** The members' sheet failed to list them, and its "Try again" was tapped. */
    data object RetrySenders : ChatSearchEvent

    /**
     * A day picked in the date picker, as days since 1970-01-01: the list shows the first message sent that day or
     * later (the last one before it when none was), as a tapped quote's message.
     */
    data class JumpToDate(
        val epochDay: Long,
    ) : ChatSearchEvent
}
