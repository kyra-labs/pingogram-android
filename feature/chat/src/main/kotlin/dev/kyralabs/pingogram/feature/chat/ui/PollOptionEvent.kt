package dev.kyralabs.pingogram.feature.chat.ui

/** Adding an answer to a poll that lets the user (td_api.tl `messagePoll.can_add_option`, `addPollOption`). */
sealed interface PollOptionEvent : ChatEvent {
    val messageId: Long

    /** "Add an option" under the poll of message [messageId]: its inline field opens. */
    data class Open(
        override val messageId: Long,
    ) : PollOptionEvent

    /** The inline field of message [messageId]'s poll closes without adding. */
    data class Dismiss(
        override val messageId: Long,
    ) : PollOptionEvent

    /** Add [text] as an answer of message [messageId]'s poll. `toString()` leaves the text out. */
    data class Add(
        override val messageId: Long,
        val text: String,
    ) : PollOptionEvent {
        override fun toString() = "Add(messageId=$messageId, …)"
    }
}
