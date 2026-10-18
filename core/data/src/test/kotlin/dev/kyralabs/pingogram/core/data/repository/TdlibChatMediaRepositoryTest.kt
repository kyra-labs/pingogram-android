package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatMediaFilter
import dev.kyralabs.pingogram.core.domain.model.ChatMediaPage
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messagePhoto
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageText
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageVideo
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateDeleteMessages
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TdlibChatMediaRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibChatMediaRepository(client)

    /** The chat's messages by id, which getMessage answers with; 404 for any other id, as for a deleted message. */
    private val messages = mutableMapOf<Long, TdApi.Message>()

    init {
        client.on(TdApi.GetMessage::class) { request ->
            messages[request.messageId]?.let { TdResult.Success(it) } ?: TdResult.Failure(TdError(404, "Not Found"))
        }
    }

    /**
     * Photos and videos with [photoIds] (every third one a video) and GIFs with [gifIds] in [CHAT], which
     * searchChatMessages finds as TDLib does: from from_message_id (included; 0 for the newest) on towards older
     * messages, shifted by the offset towards newer ones, at most limit messages, newest first. [totalCount] is what
     * TDLib counts, -1 if unknown.
     */
    private fun serveMedia(
        photoIds: List<Long>,
        gifIds: List<Long> = emptyList(),
        totalCount: Int? = null,
    ) {
        photoIds.forEachIndexed { index, id -> add(id, if (index % 3 == 2) messageVideo() else messagePhoto()) }
        gifIds.forEach { id -> add(id, messageAnimation()) }
        client.on(TdApi.SearchChatMessages::class) { request ->
            val isGifs = request.filter is TdApi.SearchMessagesFilterAnimation
            val ids = (if (isGifs) gifIds else photoIds).sortedDescending()
            val start = ids.indexOfFirst { request.fromMessageId == 0L || it <= request.fromMessageId }
            val first = (if (start < 0) ids.size else start) + request.offset
            val page = ids.subList(first.coerceIn(0, ids.size), (first + request.limit).coerceIn(0, ids.size))
            val found = page.map { messages.getValue(it) }.toTypedArray()
            TdResult.Success(TdApi.FoundChatMessages(totalCount ?: ids.size, found, page.lastOrNull() ?: 0))
        }
    }

    private fun add(
        id: Long,
        content: TdApi.MessageContent,
    ) {
        messages[id] = message(id = id, chatId = CHAT, content = content)
    }

    private fun search() = client.sentOf<TdApi.SearchChatMessages>().single()

    private fun ChatMediaPage.ids() = messages.map { it.id }

    private fun Outcome<ChatMediaPage>.page() = (this as Outcome.Success).value

    // Around

    @Test
    fun `the page around a photo is the chat's photos and videos around it, newest first`() =
        runTest {
            serveMedia(photoIds = (1L..100L).toList())

            val page = repository.around(CHAT, 50).page()

            assertEquals((70L downTo 31L).toList(), page.ids())
            assertEquals(ChatMediaFilter.PhotosAndVideos, page.filter)
            assertFalse(page.reachedOldest)
            assertFalse(page.reachedNewest)
            assertEquals(100, page.totalCount)
        }

    @Test
    fun `around asks for half a page of newer media, the message and older media, in the whole chat`() =
        runTest {
            serveMedia(photoIds = listOf(1, 2, 3))

            repository.around(CHAT, 2)

            val request = search()
            assertEquals(
                listOf<Any>(CHAT, 2L, -20, 40),
                listOf(request.chatId, request.fromMessageId, request.offset, request.limit),
            )
            assertTrue(request.filter is TdApi.SearchMessagesFilterPhotoAndVideo)
            assertEquals(listOf<Any?>(null, "", null), listOf(request.topicId, request.query, request.senderId))
        }

    @Test
    fun `a video opens among the photos and videos too`() =
        runTest {
            serveMedia(photoIds = listOf(1, 2, 3))

            val page = repository.around(CHAT, 3).page()

            assertTrue(page.messages.first().content is MessageContent.Video)
            assertEquals(ChatMediaFilter.PhotosAndVideos, page.filter)
        }

    @Test
    fun `a GIF opens among the chat's GIFs`() =
        runTest {
            serveMedia(photoIds = listOf(1, 2), gifIds = listOf(3, 5))

            val page = repository.around(CHAT, 5).page()

            assertTrue(search().filter is TdApi.SearchMessagesFilterAnimation)
            assertEquals(ChatMediaFilter.Gifs, page.filter)
            assertEquals(listOf(5L, 3L), page.ids())
        }

    @Test
    fun `around the newest media, the newest end is reached`() =
        runTest {
            serveMedia(photoIds = (1L..100L).toList())

            val page = repository.around(CHAT, 100).page()

            assertTrue(page.reachedNewest)
            assertFalse(page.reachedOldest)
            assertEquals((100L downTo 81L).toList(), page.ids())
        }

    @Test
    fun `around the oldest media, the oldest end is reached`() =
        runTest {
            serveMedia(photoIds = (1L..100L).toList())

            val page = repository.around(CHAT, 1).page()

            assertTrue(page.reachedOldest)
            assertFalse(page.reachedNewest)
        }

    @Test
    fun `an unknown total is null`() =
        runTest {
            serveMedia(photoIds = listOf(1, 2), totalCount = -1)

            assertEquals(null, repository.around(CHAT, 1).page().totalCount)
        }

    @Test
    fun `a message the search does not find is still in the page, in its place`() =
        runTest {
            serveMedia(photoIds = listOf(1, 2, 4))
            // Not among the photos TDLib's search knows, e.g. one being sent.
            add(3, messagePhoto())

            val page = repository.around(CHAT, 3).page()

            assertEquals(listOf(4L, 3L, 2L, 1L), page.ids())
        }

    @Test
    fun `a deleted message is not found, and nothing is searched`() =
        runTest {
            serveMedia(photoIds = listOf(1, 2))

            assertEquals(Outcome.Failure(DomainError.NotFound), repository.around(CHAT, 7))
            assertEquals(emptyList<TdApi.SearchChatMessages>(), client.sentOf<TdApi.SearchChatMessages>())
        }

    @Test
    fun `a message that is not media is not found`() =
        runTest {
            serveMedia(photoIds = listOf(1))
            add(2, messageText())

            assertEquals(Outcome.Failure(DomainError.NotFound), repository.around(CHAT, 2))
        }

    @Test
    fun `self-destructing media is not among the chat's media`() =
        runTest {
            serveMedia(photoIds = listOf(1))
            add(2, messagePhoto(isSecret = true))

            assertEquals(Outcome.Failure(DomainError.NotFound), repository.around(CHAT, 2))
        }

    @Test
    fun `around fails as the search fails`() =
        runTest {
            add(1, messagePhoto())
            val floodWait = TdError(429, "Too Many Requests: retry after 9")
            client.on(TdApi.SearchChatMessages::class, TdResult.Failure(floodWait))

            assertEquals(Outcome.Failure(DomainError.FloodWait(9)), repository.around(CHAT, 1))
        }

    @Test
    fun `media of another kind in the results is left out`() =
        runTest {
            serveMedia(photoIds = listOf(1, 2, 3))
            add(2, messageText())

            assertEquals(listOf(3L, 1L), repository.around(CHAT, 3).page().ids())
        }

    // Older and newer

    @Test
    fun `the older page is the media just before the message, without it`() =
        runTest {
            serveMedia(photoIds = (1L..100L).toList())

            val page = repository.older(CHAT, ChatMediaFilter.PhotosAndVideos, 61).page()

            val request = search()
            assertEquals(listOf<Any>(61L, 0, 40), listOf(request.fromMessageId, request.offset, request.limit))
            assertEquals((60L downTo 22L).toList(), page.ids())
            assertFalse(page.reachedOldest)
            assertFalse(page.reachedNewest)
        }

    @Test
    fun `an older page with nothing before the message reaches the oldest end`() =
        runTest {
            serveMedia(photoIds = listOf(5, 6))

            val page = repository.older(CHAT, ChatMediaFilter.PhotosAndVideos, 5).page()

            assertEquals(emptyList<Long>(), page.ids())
            assertTrue(page.reachedOldest)
        }

    @Test
    fun `the newer page is the media just after the message, without it`() =
        runTest {
            serveMedia(photoIds = (1L..100L).toList())

            val page = repository.newer(CHAT, ChatMediaFilter.PhotosAndVideos, 30).page()

            val request = search()
            assertEquals(listOf<Any>(30L, -40, 41), listOf(request.fromMessageId, request.offset, request.limit))
            assertEquals((70L downTo 31L).toList(), page.ids())
            assertFalse(page.reachedNewest)
            assertFalse(page.reachedOldest)
        }

    @Test
    fun `a newer page with nothing after the message reaches the newest end`() =
        runTest {
            serveMedia(photoIds = listOf(5, 6))

            val page = repository.newer(CHAT, ChatMediaFilter.PhotosAndVideos, 6).page()

            assertEquals(emptyList<Long>(), page.ids())
            assertTrue(page.reachedNewest)
        }

    @Test
    fun `GIF pages search the GIFs`() =
        runTest {
            serveMedia(photoIds = listOf(1), gifIds = listOf(2, 3, 4))

            val page = repository.newer(CHAT, ChatMediaFilter.Gifs, 2).page()

            assertTrue(search().filter is TdApi.SearchMessagesFilterAnimation)
            assertEquals(listOf(4L, 3L), page.ids())
        }

    @Test
    fun `a page fails as the search fails`() =
        runTest {
            client.on(TdApi.SearchChatMessages::class, TdResult.Failure(TdError(400, "Chat not found")))

            assertEquals(
                Outcome.Failure(DomainError.NotFound),
                repository.older(CHAT, ChatMediaFilter.PhotosAndVideos, 5),
            )
        }

    // Position

    @Test
    fun `the position is TDLib's among the media of the filter`() =
        runTest {
            client.on(TdApi.GetChatMessagePosition::class, TdResult.Success(TdApi.Count(7)))

            val position = repository.position(CHAT, ChatMediaFilter.Gifs, 42)

            val request = client.sentOf<TdApi.GetChatMessagePosition>().single()
            assertEquals(Outcome.Success(7), position)
            assertEquals(listOf<Any?>(CHAT, null, 42L), listOf(request.chatId, request.topicId, request.messageId))
            assertTrue(request.filter is TdApi.SearchMessagesFilterAnimation)
        }

    @Test
    fun `the position fails where TDLib cannot tell it`() =
        runTest {
            client.on(
                TdApi.GetChatMessagePosition::class,
                TdResult.Failure(TdError(400, "Message can't be found in the filter")),
            )

            val position = repository.position(CHAT, ChatMediaFilter.PhotosAndVideos, 42)

            assertTrue(position is Outcome.Failure)
        }

    // Deletions

    @Test
    fun `deleted messages of the chat come in batches, only those deleted for good`() =
        runTest {
            repository.deletedMessages(CHAT).test {
                client.emit(updateDeleteMessages(CHAT, 1, 2))
                client.emit(updateDeleteMessages(OTHER_CHAT, 3))
                client.emit(updateDeleteMessages(CHAT, 4, isPermanent = false, fromCache = true))
                client.emit(updateDeleteMessages(CHAT, 5))

                assertEquals(setOf(1L, 2L), awaitItem())
                assertEquals(setOf(5L), awaitItem())
            }
        }

    private companion object {
        const val CHAT = 100L
        const val OTHER_CHAT = 200L
    }
}

/** A GIF message's content. */
private fun messageAnimation() =
    TdApi.MessageAnimation(TdApiFixtures.animation(), TdApiFixtures.formattedText(""), false, false, false)
