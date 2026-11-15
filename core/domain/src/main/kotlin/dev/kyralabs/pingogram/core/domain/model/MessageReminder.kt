package dev.kyralabs.pingogram.core.domain.model

import java.time.Instant

/**
 * A reminder the user set on a message (the owner's board 29), kept on this device only and never sent to Telegram.
 *
 * @property chatId the message's chat.
 * @property messageId the message.
 * @property at when it rings.
 * @property note what the user wrote with it; empty for none.
 * @property isDone the user marked it done: it no longer rings and shows among the done ones.
 */
data class MessageReminder(
    val chatId: Long,
    val messageId: Long,
    val at: Instant,
    val note: String = "",
    val isDone: Boolean = false,
) {
    // The note is the user's own words: never in logs.
    override fun toString() = "MessageReminder(chatId=$chatId, messageId=$messageId, at=$at, isDone=$isDone)"

    /** The most characters a note keeps. */
    companion object {
        const val NOTE_LENGTH_MAX = 200
    }
}
