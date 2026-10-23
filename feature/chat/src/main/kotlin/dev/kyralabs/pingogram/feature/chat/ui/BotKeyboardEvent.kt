package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonPosition

/** What the user does with a bot's keyboard in the system keyboard's place, sent to [ChatViewModel.onEvent]. */
sealed interface BotKeyboardEvent : ChatEvent {
    /** The button at [position] of the keyboard of message [messageId] was tapped. */
    data class Press(
        val messageId: Long,
        val position: BotButtonPosition,
    ) : BotKeyboardEvent

    /** The phone number question was answered "Share": the user's own contact goes to the chat. */
    data object SharePhone : BotKeyboardEvent

    /** The phone number question was dismissed: nothing is sent. */
    data object DismissDialog : BotKeyboardEvent

    /** The search of the users or chat picker changed to [query]. */
    data class SearchTargets(
        val query: String,
    ) : BotKeyboardEvent {
        override fun toString() = "SearchTargets(…)"
    }

    /** The picker's list came near its end: the next page of chats loads. */
    data object LoadMoreTargets : BotKeyboardEvent

    /** Chat [chatId] (a user's private chat, or a group or channel) was picked, or unpicked. */
    data class ToggleTarget(
        val chatId: Long,
    ) : BotKeyboardEvent

    /** The picker's "Share": what was picked goes to the bot. */
    data object Share : BotKeyboardEvent

    /** The picker was dismissed: nothing is shared. */
    data object DismissShare : BotKeyboardEvent
}

/** One-off effects of a bot's keyboard. */
sealed interface BotKeyboardEffect : ChatEffect {
    /**
     * A button asks for the user's location: the attach menu's "Location" sheet opens, with its permission step, and
     * sends only once the user confirms there.
     */
    data object ShareLocation : BotKeyboardEffect
}
