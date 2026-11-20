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

/** How [TdlibStoryAlbumRepository] reads a chat's profile stories and story albums, and edits its albums. */
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
    fun `an album's story count is read with a page of one story`() =
        runTest {
            client.on(
                TdApi.GetStoryAlbumStories::class,
                TdResult.Success(TdApi.Stories(14, arrayOf(story()), intArrayOf())),
            )

            val count = repository.storyCount(CHAT_ID, albumId = 2)

            assertEquals(Outcome.Success(14), count)
            val sent = client.sentOf<TdApi.GetStoryAlbumStories>().single()
            assertEquals(listOf(2, 0, 1), listOf(sent.storyAlbumId, sent.offset, sent.limit))
        }

    @Test
    fun `pages the stories a chat keeps on its profile`() =
        runTest {
            client.on(
                TdApi.GetChatPostedToChatPageStories::class,
                TdResult.Success(TdApi.Stories(31, arrayOf(story(id = 8), story(id = 6)), intArrayOf(8))),
            )

            val page = (repository.profileStories(CHAT_ID, fromStoryId = 9, limit = 30) as Outcome.Success).value

            val sent = client.sentOf<TdApi.GetChatPostedToChatPageStories>().single()
            assertEquals(listOf(CHAT_ID, 9L, 30L), listOf(sent.chatId, sent.fromStoryId.toLong(), sent.limit.toLong()))
            assertEquals(31, page.totalCount)
            assertEquals(listOf(8, 6), page.stories.map { it.id })
        }

    @Test
    fun `creates, renames, fills, empties and deletes an album`() =
        runTest {
            val album = TdApi.StoryAlbum(7, "Hampi", null, null)
            client.on(TdApi.CreateStoryAlbum::class, TdResult.Success(album))
            client.on(TdApi.SetStoryAlbumName::class, TdResult.Success(TdApi.StoryAlbum(7, "Hampi trip", null, null)))
            client.on(TdApi.AddStoryAlbumStories::class, TdResult.Success(album))
            client.on(TdApi.RemoveStoryAlbumStories::class, TdResult.Success(album))
            client.on(TdApi.DeleteStoryAlbum::class, TdResult.Success(TdApi.Ok()))

            val created = (repository.createAlbum(CHAT_ID, "Hampi", listOf(3, 1)) as Outcome.Success).value
            val renamed = (repository.renameAlbum(CHAT_ID, 7, "Hampi trip") as Outcome.Success).value
            repository.addStories(CHAT_ID, 7, listOf(5))
            repository.removeStories(CHAT_ID, 7, listOf(1))
            val deleted = repository.deleteAlbum(CHAT_ID, 7)

            assertEquals(7 to "Hampi", created.id to created.name)
            assertEquals("Hampi trip", renamed.name)
            assertEquals(Outcome.Success(Unit), deleted)
            val create = client.sentOf<TdApi.CreateStoryAlbum>().single()
            assertEquals(listOf(3, 1), create.storyIds.toList())
            assertEquals(CHAT_ID to "Hampi", create.storyPosterChatId to create.name)
            assertEquals(
                listOf(5),
                client
                    .sentOf<TdApi.AddStoryAlbumStories>()
                    .single()
                    .storyIds
                    .toList(),
            )
            assertEquals(
                listOf(1),
                client
                    .sentOf<TdApi.RemoveStoryAlbumStories>()
                    .single()
                    .storyIds
                    .toList(),
            )
            assertEquals(7, client.sentOf<TdApi.DeleteStoryAlbum>().single().storyAlbumId)
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
