package dev.kyralabs.pingogram.core.domain.model

/**
 * A reminder of a message, kept on this device only, never on Telegram's servers (`docs/DESIGN_SYSTEM.md` §6
 * "Message reminders", the owner's board 29): message [messageId] of chat [chatId], due at [at] (Unix seconds), with
 * the user's [note] and what the message said when it was set ([preview]); [done] once it rang or the user marked it
 * done. A message has one reminder at most: setting another replaces it.
 *
 * `toString()` leaves the note and the preview out, so a reminder can be logged.
 */
data class Reminder(
    val chatId: Long,
    val messageId: Long,
    val at: Long,
    val note: String = "",
    val preview: BookmarkPreview = BookmarkPreview(),
    val done: Boolean = false,
) {
    override fun toString() = "Reminder(chatId=$chatId, messageId=$messageId, at=$at, done=$done)"
}
