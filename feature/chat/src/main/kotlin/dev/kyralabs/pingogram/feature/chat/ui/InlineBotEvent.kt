package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.SendOptions

/** What the user does with an inline bot's results ("@gif cats", `REQUIREMENTS.md` FR-C5), sent to [ChatViewModel]. */
sealed interface InlineBotEvent : ChatEvent {
    /**
     * Result [resultId] of the page with [queryId] was tapped (or picked with a hardware keyboard): it is sent with
     * [options] (from its menu: without sound, or at a time), replying where the composer replies, and the field
     * clears. A game is not sent: a snackbar says Pingogram cannot play games yet.
     */
    data class Send(
        val queryId: Long,
        val resultId: String,
        val options: SendOptions = SendOptions(),
    ) : InlineBotEvent

    /** The list neared its end: the next page loads, if there is one; after a failed page, it loads again. */
    data object LoadMore : InlineBotEvent

    /** "Try again" after the answer failed. */
    data object Retry : InlineBotEvent

    /**
     * The bot's button above its results: "Start" opens the bot's chat, where "Start" is ready and sends nothing until
     * tapped; a Mini App's button gets a snackbar that Pingogram cannot open Mini Apps yet.
     */
    data object OpenButton : InlineBotEvent

    /**
     * The user answered whether the bot that asks for it may know their location: [share] lets its queries carry it
     * (Android was asked for the permission first, where it was needed); "Not now" and dismissing the question query
     * without it. Kept for the process.
     */
    data class AnswerLocation(
        val share: Boolean,
    ) : InlineBotEvent
}
