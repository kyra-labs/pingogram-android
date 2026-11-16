package dev.kyralabs.pingogram.core.domain.model

/**
 * A message that had one of the user's keyword alert words (board 30): message [messageId] of chat [chatId], sent at
 * [date] (Unix seconds), first matching [word] as the user typed it. Kept on this phone only, for a week, never sent.
 * `toString()` leaves the word out.
 */
data class KeywordHit(
    val chatId: Long,
    val messageId: Long,
    val word: String,
    val date: Long,
) {
    override fun toString() = "KeywordHit(chatId=$chatId, messageId=$messageId, date=$date)"
}

/**
 * A new [hit] as it arrives, with the message's [text] (or caption) and where the word is in it ([start] to [end]),
 * and whether its chat [isMuted]: what a keyword alert's notification shows. `toString()` leaves the text out.
 */
data class KeywordAlert(
    val hit: KeywordHit,
    val text: String,
    val start: Int,
    val end: Int,
    val isMuted: Boolean,
) {
    override fun toString() = "KeywordAlert(hit=$hit, isMuted=$isMuted)"
}
