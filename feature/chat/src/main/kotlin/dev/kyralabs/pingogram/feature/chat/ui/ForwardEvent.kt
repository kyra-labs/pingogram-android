package dev.kyralabs.pingogram.feature.chat.ui

/** What the user does in the forward sheet (`REQUIREMENTS.md` FR-C6), sent to [ChatViewModel.onEvent]. */
sealed interface ForwardEvent : ChatEvent {
    /** The search field holds [query] now: the chats it finds replace the list once it stays so. */
    data class QueryChanged(
        val query: String,
    ) : ForwardEvent {
        override fun toString() = "QueryChanged(…)"
    }

    /** A chat was tapped: it is picked, or unpicked if it was. */
    data class ToggleTarget(
        val chatId: Long,
    ) : ForwardEvent

    /** "Hide sender's name": the messages go as copies, without "Forwarded from". */
    data class SetHideSenderName(
        val hide: Boolean,
    ) : ForwardEvent

    /** "Hide captions": the copies go without their captions. */
    data class SetHideCaptions(
        val hide: Boolean,
    ) : ForwardEvent

    /** The list comes near its end: the next page of the chat list loads. */
    data object LoadMore : ForwardEvent

    /** The send button: the messages go to the picked chats, after the comment. */
    data object Send : ForwardEvent

    /** The sheet was dismissed: nothing is forwarded. */
    data object Dismiss : ForwardEvent
}
