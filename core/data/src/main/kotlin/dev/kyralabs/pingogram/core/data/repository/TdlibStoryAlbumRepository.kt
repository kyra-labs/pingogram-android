package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toMinithumbnail
import dev.kyralabs.pingogram.core.data.mapper.toPhoto
import dev.kyralabs.pingogram.core.data.mapper.toStory
import dev.kyralabs.pingogram.core.data.mapper.toThumbnail
import dev.kyralabs.pingogram.core.domain.model.StoryAlbum
import dev.kyralabs.pingogram.core.domain.model.StoryAlbumCover
import dev.kyralabs.pingogram.core.domain.model.StoryAlbumPage
import dev.kyralabs.pingogram.core.domain.repository.StoryAlbumRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [StoryAlbumRepository] on TDLib's story album calls and `getChatPostedToChatPageStories`, without a cache: td_api.tl
 * has no update for albums or profile stories, and TDLib keeps what it fetched (`docs/TDLIB.md` "Profile stories and
 * story albums").
 */
@AccountScoped
class TdlibStoryAlbumRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : StoryAlbumRepository {
        override suspend fun albums(chatId: Long): Outcome<ImmutableList<StoryAlbum>> =
            client.request(TdApi.GetChatStoryAlbums(chatId)).map { found ->
                found.albums
                    .orEmpty()
                    .map { it.toStoryAlbum() }
                    .toImmutableList()
            }

        override suspend fun stories(
            chatId: Long,
            albumId: Int,
            offset: Int,
            limit: Int,
        ): Outcome<StoryAlbumPage> =
            client.request(TdApi.GetStoryAlbumStories(chatId, albumId, offset, limit)).map { it.toPage() }

        override suspend fun storyCount(
            chatId: Long,
            albumId: Int,
        ): Outcome<Int> = client.request(TdApi.GetStoryAlbumStories(chatId, albumId, 0, 1)).map { it.totalCount }

        override suspend fun profileStories(
            chatId: Long,
            fromStoryId: Int,
            limit: Int,
        ): Outcome<StoryAlbumPage> =
            client.request(TdApi.GetChatPostedToChatPageStories(chatId, fromStoryId, limit)).map { it.toPage() }

        override suspend fun createAlbum(
            chatId: Long,
            name: String,
            storyIds: List<Int>,
        ): Outcome<StoryAlbum> =
            client.request(TdApi.CreateStoryAlbum(chatId, name, storyIds.toIntArray())).map { it.toStoryAlbum() }

        override suspend fun renameAlbum(
            chatId: Long,
            albumId: Int,
            name: String,
        ): Outcome<StoryAlbum> =
            client.request(TdApi.SetStoryAlbumName(chatId, albumId, name)).map { it.toStoryAlbum() }

        override suspend fun addStories(
            chatId: Long,
            albumId: Int,
            storyIds: List<Int>,
        ): Outcome<StoryAlbum> =
            client
                .request(TdApi.AddStoryAlbumStories(chatId, albumId, storyIds.toIntArray()))
                .map { it.toStoryAlbum() }

        override suspend fun removeStories(
            chatId: Long,
            albumId: Int,
            storyIds: List<Int>,
        ): Outcome<StoryAlbum> =
            client
                .request(TdApi.RemoveStoryAlbumStories(chatId, albumId, storyIds.toIntArray()))
                .map { it.toStoryAlbum() }

        override suspend fun deleteAlbum(
            chatId: Long,
            albumId: Int,
        ): Outcome<Unit> = client.request(TdApi.DeleteStoryAlbum(chatId, albumId)).map {}
    }

private fun TdApi.Stories.toPage(): StoryAlbumPage =
    StoryAlbumPage(
        totalCount = totalCount,
        stories =
            stories
                .orEmpty()
                .map { it.toStory() }
                .toImmutableList(),
    )

private fun TdApi.StoryAlbum.toStoryAlbum(): StoryAlbum =
    StoryAlbum(
        id = id,
        name = name.orEmpty(),
        cover =
            photoIcon?.let { StoryAlbumCover.Photo(it.toPhoto()) }
                ?: videoIcon?.let {
                    StoryAlbumCover.Video(it.minithumbnail?.toMinithumbnail(), it.thumbnail?.toThumbnail())
                },
    )
