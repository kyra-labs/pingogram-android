package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.StoryAlbum
import dev.kyralabs.pingogram.core.domain.model.StoryAlbumPage
import kotlinx.collections.immutable.ImmutableList

/**
 * The story albums of a chat (a user's private chat, a channel or a supergroup) and their stories (boards 11a, 11b),
 * read only. td_api.tl announces no change to them, so each call asks TDLib again.
 */
interface StoryAlbumRepository {
    /** The albums of chat [chatId], in its owner's order; empty without any. */
    suspend fun albums(chatId: Long): Outcome<ImmutableList<StoryAlbum>>

    /** At most [limit] stories of album [albumId] of chat [chatId], from the [offset]-th, in the album's order. */
    suspend fun stories(
        chatId: Long,
        albumId: Int,
        offset: Int,
        limit: Int,
    ): Outcome<StoryAlbumPage>
}
