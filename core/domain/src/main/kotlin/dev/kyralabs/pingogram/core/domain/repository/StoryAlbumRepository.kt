package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.StoryAlbum
import dev.kyralabs.pingogram.core.domain.model.StoryAlbumPage
import kotlinx.collections.immutable.ImmutableList

/**
 * A chat's stories on its profile (a user's private chat, a channel or a supergroup) and its story albums with their
 * stories (`docs/TDLIB.md` "Profile stories and story albums"). td_api.tl announces no change to either, so each call
 * asks TDLib again. Only the albums of the user's own profile can be changed.
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

    /** How many stories album [albumId] of chat [chatId] holds, which td_api.tl gives only with a page of them. */
    suspend fun storyCount(
        chatId: Long,
        albumId: Int,
    ): Outcome<Int>

    /**
     * At most [limit] of the stories chat [chatId] keeps on its profile, pinned ones first and then newest first: from
     * the start for [fromStoryId] 0, otherwise those after story [fromStoryId].
     */
    suspend fun profileStories(
        chatId: Long,
        fromStoryId: Int,
        limit: Int,
    ): Outcome<StoryAlbumPage>

    /** Creates album [name] of chat [chatId] holding [storyIds], in that order. */
    suspend fun createAlbum(
        chatId: Long,
        name: String,
        storyIds: List<Int>,
    ): Outcome<StoryAlbum>

    /** Renames album [albumId] of chat [chatId] to [name]. */
    suspend fun renameAlbum(
        chatId: Long,
        albumId: Int,
        name: String,
    ): Outcome<StoryAlbum>

    /** Adds [storyIds] to the start of album [albumId] of chat [chatId]. */
    suspend fun addStories(
        chatId: Long,
        albumId: Int,
        storyIds: List<Int>,
    ): Outcome<StoryAlbum>

    /** Takes [storyIds] out of album [albumId] of chat [chatId]; the stories themselves stay. */
    suspend fun removeStories(
        chatId: Long,
        albumId: Int,
        storyIds: List<Int>,
    ): Outcome<StoryAlbum>

    /** Deletes album [albumId] of chat [chatId]; its stories stay on the profile. */
    suspend fun deleteAlbum(
        chatId: Long,
        albumId: Int,
    ): Outcome<Unit>
}
