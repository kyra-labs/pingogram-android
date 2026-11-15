package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Bookmark
import dev.kyralabs.pingogram.core.domain.model.BookmarkTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The account's bookmarks and their tags (the owner's board 34), kept on this device only: Telegram never sees them,
 * and they go when the account logs out. Writes fail with `DomainError.Unknown` when the file cannot be written.
 */
interface BookmarkRepository {
    /** Every bookmark, the newest first (owner decision 2026-11-15), then every change. */
    val bookmarks: Flow<ImmutableList<Bookmark>>

    /** The tags, in the user's order, then every change. */
    val tags: Flow<ImmutableList<BookmarkTag>>

    /** The bookmark of message [messageId] of chat [chatId], or null, then every change. */
    fun bookmark(
        chatId: Long,
        messageId: Long,
    ): Flow<Bookmark?>

    /** Saves [bookmark], in place of the message's earlier one if any; tags it names that do not exist are dropped. */
    suspend fun save(bookmark: Bookmark): Outcome<Unit>

    /** Removes the bookmark of message [messageId] of chat [chatId]; nothing to do without one. */
    suspend fun remove(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit>

    /** Adds a tag named [name] (trimmed) in [color] at the end of the tags, and answers it. */
    suspend fun addTag(
        name: String,
        color: Int,
    ): Outcome<BookmarkTag>

    /** Renames or recolors [tag], which keeps its place. */
    suspend fun updateTag(tag: BookmarkTag): Outcome<Unit>

    /** Deletes tag [id]: its bookmarks stay, without it. */
    suspend fun removeTag(id: Long): Outcome<Unit>

    /** Puts the tags in the order of [ids]; tags it leaves out keep their order after them. */
    suspend fun reorderTags(ids: List<Long>): Outcome<Unit>
}
