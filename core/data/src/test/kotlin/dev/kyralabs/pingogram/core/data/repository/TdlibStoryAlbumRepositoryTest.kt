package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.StoryAlbumCover
import dev.kyralabs.pingogram.core.domain.model.StoryContent
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.StoryFixtures.story
import dev.kyralabs.pingogram.core.testing.StoryFixtures.videoContent
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.photo
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.video
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** How [TdlibStoryAlbumRepository] reads a chat's story albums and pages an album's stories. */
class TdlibStoryAlbumRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibStoryAlbumRepository(client)

    @Test
    fun `lists a chat's albums with their covers, in order`() =
        runTest {
            client.on(
                TdApi.GetChatStoryAlbums::class,
                TdResult.Success(
                    TdApi.StoryAlbums(
                        arrayOf(
                            TdApi.StoryAlbum(1, "Hampi trip", photo(), null),
                            TdApi.StoryAlbum(2, "Coffee runs", null, video()),
                            TdApi.StoryAlbum(3, "Monsoon", null, null),
                        ),
                    ),
                ),
            )

            val albums = (repository.albums(CHAT_ID) as Outcome.Success).value

            assertEquals(CHAT_ID, client.sentOf<TdApi.GetChatStoryAlbums>().single().chatId)
            assertEquals(listOf("Hampi trip", "Coffee runs", "Monsoon"), albums.map { it.name })
            assertInstanceOf(StoryAlbumCover.Photo::class.java, albums[0].cover)
            val video = albums[1].cover as StoryAlbumCover.Video
            assertEquals(320, video.thumbnail?.width)
            assertNull(albums[2].cover)
        }

    @Test
    fun `pages an album's stories with its total`() =
        runTest {
            client.on(
                TdApi.GetStoryAlbumStories::class,
                TdResult.Success(
                    TdApi.Stories(9, arrayOf(story(id = 4), story(id = 5, content = videoContent())), intArrayOf()),
                ),
            )

            val page = (repository.stories(CHAT_ID, albumId = 1, offset = 20, limit = 30) as Outcome.Success).value

            val sent = client.sentOf<TdApi.GetStoryAlbumStories>().single()
            assertEquals(CHAT_ID, sent.chatId)
            assertEquals(listOf(1, 20, 30), listOf(sent.storyAlbumId, sent.offset, sent.limit))
            assertEquals(9, page.totalCount)
            assertEquals(listOf(4, 5), page.stories.map { it.id })
            assertInstanceOf(StoryContent.Video::class.java, page.stories[1].content)
        }

    @Test
    fun `a refusal comes back as the domain's error`() =
        runTest {
            client.on(TdApi.GetChatStoryAlbums::class, TdResult.Failure(TdError(400, "CHAT_ID_INVALID")))

            val failed = repository.albums(CHAT_ID)

            assertInstanceOf(DomainError::class.java, (failed as Outcome.Failure).error)
        }

    private companion object {
        const val CHAT_ID = 42L
    }
}
