package dev.kyralabs.pingogram.feature.chat.ui

/** What the user does with the chat's bots (`REQUIREMENTS.md` FR-C5), sent to [ChatViewModel.onEvent]. */
sealed interface BotEvent : ChatEvent {
    /**
     * The bot button at the start of the composer's pill: the commands sheet, or for a bot's Mini App menu button a
     * snackbar that Pingogram cannot open Mini Apps yet.
     */
    data object OpenMenu : BotEvent

    /** The commands sheet was dismissed. */
    data object DismissCommands : BotEvent

    /**
     * A command tapped in the sheet or chosen among the suggestions: [text] ("/start", "/start@helper_bot") is sent as
     * a message, replying where the composer replies; a command being typed in the field goes with it.
     */
    data class SendCommand(
        val text: String,
    ) : BotEvent

    /**
     * A command's insert button, or Tab on a suggestion: [text] goes into the field, followed by a space, for the user
     * to add its arguments; the field takes the focus.
     */
    data class InsertCommand(
        val text: String,
    ) : BotEvent

    /** "Start" in an empty bot chat or after a start link: the bot is started, with the link's parameter if any. */
    data object Start : BotEvent
}
