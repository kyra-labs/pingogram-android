package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * A message the user bookmarked, kept on this device only, never on Telegram's servers (`docs/DESIGN_SYSTEM.md` §6
 * "Bookmarks", the owner's board 34): message [messageId] of chat [chatId], saved at [savedAt] (Unix seconds), with the
 * tags of [tagIds] ([BookmarkTag.id]; none is allowed, owner decision 2026-11-15), the user's [note] and what the
 * message said when it was saved ([preview]), shown when the message is gone or not loaded.
 *
 * `toString()` leaves the note and the preview out, so a bookmark can be logged.
 */
data class Bookmark(
    val chatId: Long,
    val messageId: Long,
    val savedAt: Long,
    val tagIds: ImmutableList<Long> = persistentListOf(),
    val note: String = "",
    val preview: BookmarkPreview = BookmarkPreview(),
) {
    override fun toString() = "Bookmark(chatId=$chatId, messageId=$messageId, tags=${tagIds.size})"
}

/** What a bookmarked message said when it was saved: who sent it ([sender], empty when unknown) and one line of it. */
data class BookmarkPreview(
    val sender: String = "",
    val text: String = "",
)

/**
 * A tag of bookmarks: its [name] and its [color], one of the sender hues (`PingogramColors.senderHues`, 0 until 8),
 * drawn in the theme's own tones.
 */
data class BookmarkTag(
    val id: Long,
    val name: String,
    val color: Int,
) {
    override fun toString() = "BookmarkTag(id=$id, color=$color)"
}
