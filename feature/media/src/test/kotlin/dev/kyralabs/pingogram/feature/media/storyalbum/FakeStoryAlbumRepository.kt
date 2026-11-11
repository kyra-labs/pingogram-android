package dev.kyralabs.pingogram.feature.media.storyalbum

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.Story
import dev.kyralabs.pingogram.core.domain.model.StoryAlbum
import dev.kyralabs.pingogram.core.domain.model.StoryAlbumPage
import dev.kyralabs.pingogram.core.domain.model.StoryContent
import dev.kyralabs.pingogram.core.domain.repository.StoryAlbumRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Albums and an album's stories set by the tests: [stories] are paged by the offsets asked ([requests]), out of
 * [totalCount]; [failNext] makes the next page fail.
 */
internal class FakeStoryAlbumRepository : StoryAlbumRepository {
    var albums: Outcome<ImmutableList<StoryAlbum>> = Outcome.Success(persistentListOf())
    var stories: List<Story> = emptyList()
    var totalCount: Int? = null
    var failNext: Outcome.Failure? = null
    val requests = mutableListOf<Pair<Int, Int>>()

    override suspend fun albums(chatId: Long): Outcome<ImmutableList<StoryAlbum>> = albums

    override suspend fun stories(
        chatId: Long,
        albumId: Int,
        offset: Int,
        limit: Int,
    ): Outcome<StoryAlbumPage> {
        requests += offset to limit
        failNext?.let {
            failNext = null
            return it
        }
        val page = stories.drop(offset).take(limit).toImmutableList()
        return Outcome.Success(StoryAlbumPage(totalCount ?: stories.size, page))
    }

    companion object {
        /** A photo story [id], posted at [date] (Unix seconds). */
        fun story(
            id: Int,
            date: Long,
        ) = Story(
            id = id,
            posterChatId = 42,
            date = date,
            content = StoryContent.Photo(Photo(persistentListOf(), minithumbnail = null)),
            caption = FormattedText.Empty,
            canBeReplied = false,
            chosenReaction = null,
        )
    }
}
