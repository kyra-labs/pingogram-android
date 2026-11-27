package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A channel's posts after the last one read: newer pages, oldest first, never the chat opened or a post viewed. */
class TdlibUnreadPostsRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibUnreadPostsRepository(client)

    /** A channel whose posts are [ids]; each page answers as TDLib does, the newest first, older ones included. */
    private fun channel(vararg ids: Long) =
        client.on(TdApi.GetChatHistory::class) { request ->
            val newest = ids.filter { it > request.fromMessageId }.take(-request.offset)
            val older = ids.filter { it <= request.fromMessageId }.sortedDescending()
            val page = (newest.sortedDescending() + older).take(request.limit)
            TdResult.Success(TdApi.Messages(page.size, page.map { message(id = it, chatId = CHAT) }.toTypedArray()))
        }

    private fun Outcome<List<Message>>.ids() = (this as Outcome.Success).value.map(Message::id)

    @Test
    fun `reads the posts after the last read one, oldest first`() =
        runTest {
            channel(1, 2, 3, 4, 5)

            assertEquals(listOf(4L, 5L), repository.postsAfter(CHAT, afterMessageId = 3, limit = 10).ids())
            val sent = client.sentOf<TdApi.GetChatHistory>().first()
            assertEquals(CHAT, sent.chatId)
            assertEquals(3L, sent.fromMessageId)
            assertEquals(-99, sent.offset)
            assertEquals(100, sent.limit)
            assertFalse(sent.onlyLocal)
        }

    @Test
    fun `reads on from the newest of each page, up to the limit`() =
        runTest {
            channel(*(1L..250L).toList().toLongArray())

            val posts = repository.postsAfter(CHAT, afterMessageId = 10, limit = 150).ids()

            assertEquals((11L..160L).toList(), posts)
            assertEquals(listOf(10L, 109L), client.sentOf<TdApi.GetChatHistory>().map { it.fromMessageId })
        }

    @Test
    fun `nothing after the last read post is no posts`() =
        runTest {
            channel(1, 2, 3)

            assertEquals(emptyList<Long>(), repository.postsAfter(CHAT, afterMessageId = 3, limit = 10).ids())
        }

    @Test
    fun `never opens the chat or views its posts`() =
        runTest {
            channel(1, 2, 3)

            repository.postsAfter(CHAT, afterMessageId = 1, limit = 10)

            assertTrue(client.sentOf<TdApi.OpenChat>().isEmpty())
            assertTrue(client.sentOf<TdApi.ViewMessages>().isEmpty())
        }

    @Test
    fun `fails as TDLib does when nothing could be read, and keeps what was read before a failure`() =
        runTest {
            client.on(TdApi.GetChatHistory::class, TdResult.Failure(TdError(500, "Network error")))

            assertTrue(repository.postsAfter(CHAT, afterMessageId = 1, limit = 10) is Outcome.Failure)

            client.on(TdApi.GetChatHistory::class) { request ->
                if (request.fromMessageId == 1L) {
                    TdResult.Success(TdApi.Messages(1, arrayOf(message(id = 2, chatId = CHAT))))
                } else {
                    TdResult.Failure(TdError(429, "Too Many Requests: retry after 5"))
                }
            }

            assertEquals(listOf(2L), repository.postsAfter(CHAT, afterMessageId = 1, limit = 10).ids())
        }

    private companion object {
        const val CHAT = 100L
    }
}
