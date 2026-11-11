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
 * [StoryAlbumRepository] on TDLib's `getChatStoryAlbums` and `getStoryAlbumStories`, without a cache: td_api.tl has no
 * update for albums, and TDLib keeps what it fetched. Albums are only read; Pingogram creates and edits none yet.
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
            client.request(TdApi.GetStoryAlbumStories(chatId, albumId, offset, limit)).map { page ->
                StoryAlbumPage(
                    totalCount = page.totalCount,
                    stories =
                        page.stories
                            .orEmpty()
                            .map { it.toStory() }
                            .toImmutableList(),
                )
            }
    }

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
