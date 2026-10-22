package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonPosition

/** What the user does with the buttons bots attach under messages, sent to [ChatViewModel.onEvent]. */
sealed interface BotButtonEvent : ChatEvent {
    /** The button at [position] under message [messageId] was tapped. */
    data class Press(
        val messageId: Long,
        val position: BotButtonPosition,
    ) : BotButtonEvent

    /** The link of the open link question was confirmed: it opens. */
    data object OpenLink : BotButtonEvent

    /**
     * The login question was answered: the link opens, with the user logged in to its site if [logIn], letting the
     * bot send them messages if [allowWriteAccess].
     */
    data class Login(
        val logIn: Boolean,
        val allowWriteAccess: Boolean,
    ) : BotButtonEvent

    /** The password typed into the password question goes to the bot's button. */
    data object SubmitPassword : BotButtonEvent

    /** The open alert or question was dismissed: nothing more happens. */
    data object DismissDialog : BotButtonEvent

    /** The search of the switch-inline chat picker changed to [query]. */
    data class SearchChats(
        val query: String,
    ) : BotButtonEvent {
        override fun toString() = "SearchChats(…)"
    }

    /** The switch-inline chat picker's list came near its end: the next page of chats loads. */
    data object LoadMoreChats : BotButtonEvent

    /** Chat [chatId] was picked: it opens with the inline query in its composer. */
    data class PickChat(
        val chatId: Long,
    ) : BotButtonEvent

    /** The switch-inline chat picker was dismissed. */
    data object DismissChats : BotButtonEvent
}

/** One-off effects of bots' buttons: snackbars. */
sealed interface BotButtonEffect : ChatEffect {
    /** The bot answered a callback with [text], shown in a snackbar. */
    data class Answer(
        val text: String,
    ) : BotButtonEffect {
        override fun toString() = "Answer(…)"
    }

    /** The button opens something Pingogram does not offer yet ([what]): a snackbar says so. */
    data class Unsupported(
        val what: UnsupportedButton,
    ) : BotButtonEffect
}

/** What a button opens that Pingogram does not offer yet (`docs/ROADMAP.md`, Backlog). */
enum class UnsupportedButton {
    MiniApp,
    Game,
    Payment,

    /** A switch-inline button whose chat a Telegram link names. */
    InlineLink,
}
