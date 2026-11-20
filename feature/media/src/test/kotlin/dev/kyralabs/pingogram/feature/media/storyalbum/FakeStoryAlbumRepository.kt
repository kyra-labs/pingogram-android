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
 * Albums, an album's stories and a profile's set by the tests: [stories] are paged by the offsets asked ([requests]),
 * out of [totalCount]; [failNext] makes the next album page fail.
 */
internal class FakeStoryAlbumRepository : StoryAlbumRepository {
    var albums: Outcome<ImmutableList<StoryAlbum>> = Outcome.Success(persistentListOf())
    var stories: List<Story> = emptyList()
    var totalCount: Int? = null
    var failNext: Outcome.Failure? = null
    val requests = mutableListOf<Pair<Int, Int>>()

    /** The stories on the chat's profile, paged after the story asked for ([profileRequests]). */
    var profile: List<Story> = emptyList()
    val profileRequests = mutableListOf<Pair<Int, Int>>()

    /** Makes the next profile page fail. */
    var profileFailure: Outcome.Failure? = null

    /** Makes every album's count fail. */
    var countFailure: Outcome.Failure? = null

    /** The albums created and changed, as "create:Hampi:[9, 4]"; [changeFailure] refuses them all. */
    val changes = mutableListOf<String>()
    var changeFailure: Outcome.Failure? = null

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

    override suspend fun storyCount(
        chatId: Long,
        albumId: Int,
    ): Outcome<Int> = countFailure ?: Outcome.Success(totalCount ?: stories.size)

    override suspend fun profileStories(
        chatId: Long,
        fromStoryId: Int,
        limit: Int,
    ): Outcome<StoryAlbumPage> {
        profileRequests += fromStoryId to limit
        profileFailure?.let {
            profileFailure = null
            return it
        }
        val page = profile.dropWhile { fromStoryId != 0 && it.id != fromStoryId }.drop(if (fromStoryId == 0) 0 else 1)
        return Outcome.Success(StoryAlbumPage(profile.size, page.take(limit).toImmutableList()))
    }

    override suspend fun createAlbum(
        chatId: Long,
        name: String,
        storyIds: List<Int>,
    ): Outcome<StoryAlbum> = change("create:$name:$storyIds") { StoryAlbum(id = 1, name = name, cover = null) }

    override suspend fun renameAlbum(
        chatId: Long,
        albumId: Int,
        name: String,
    ): Outcome<StoryAlbum> = change("rename:$albumId:$name") { StoryAlbum(id = albumId, name = name, cover = null) }

    override suspend fun addStories(
        chatId: Long,
        albumId: Int,
        storyIds: List<Int>,
    ): Outcome<StoryAlbum> = change("add:$albumId:$storyIds") { StoryAlbum(id = albumId, name = "", cover = null) }

    override suspend fun removeStories(
        chatId: Long,
        albumId: Int,
        storyIds: List<Int>,
    ): Outcome<StoryAlbum> = change("remove:$albumId:$storyIds") { StoryAlbum(id = albumId, name = "", cover = null) }

    override suspend fun deleteAlbum(
        chatId: Long,
        albumId: Int,
    ): Outcome<Unit> = change("delete:$albumId") {}

    /** Records [call] in [changes], and answers with [changeFailure] if set, else [result]. */
    private fun <T> change(
        call: String,
        result: () -> T,
    ): Outcome<T> {
        changes += call
        return changeFailure ?: Outcome.Success(result())
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
