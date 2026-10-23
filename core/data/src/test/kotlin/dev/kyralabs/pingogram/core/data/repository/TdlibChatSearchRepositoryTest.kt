package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatSearchCursor
import dev.kyralabs.pingogram.core.domain.model.ChatSearchPage
import dev.kyralabs.pingogram.core.domain.model.ChatSearchQuery
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** Search in one chat: pages of found messages, secret chats on the device, and the message to show for a day. */
class TdlibChatSearchRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibChatSearchRepository(client)

    private fun found(
        vararg ids: Long,
        total: Int = ids.size,
        next: Long = 0,
    ) = TdResult.Success(
        TdApi.FoundChatMessages(total, ids.map { message(id = it, chatId = CHAT) }.toTypedArray(), next),
    )

    @Test
    fun `searches the chat for the words, newest first, with the next page's start`() =
        runTest {
            client.on(TdApi.SearchChatMessages::class, found(9, 7, 5, total = 17, next = 5))

            assertEquals(
                Outcome.Success(ChatSearchPage(persistentListOf(9L, 7L, 5L), 17, ChatSearchCursor.FromMessage(5))),
                repository.search(CHAT, ChatSearchQuery("  cats ")),
            )
            val sent = client.sentOf<TdApi.SearchChatMessages>().single()
            assertEquals(CHAT, sent.chatId)
            assertEquals("cats", sent.query)
            assertEquals(0L, sent.fromMessageId)
            assertEquals(0, sent.offset)
            assertEquals(50, sent.limit)
            assertNull(sent.senderId)
            assertNull(sent.topicId)
            assertNull(sent.filter)
        }

    @Test
    fun `the next page starts where the last one ended, and the last page has none after it`() =
        runTest {
            client.on(TdApi.SearchChatMessages::class, found(4, 2))

            val page = repository.search(CHAT, ChatSearchQuery("cats"), ChatSearchCursor.FromMessage(5))

            assertEquals(Outcome.Success(ChatSearchPage(persistentListOf(4L, 2L), 2, next = null)), page)
            assertEquals(5L, client.sentOf<TdApi.SearchChatMessages>().single().fromMessageId)
        }

    @Test
    fun `filters by sender, also without words, and searches a topic`() =
        runTest {
            client.on(TdApi.SearchChatMessages::class, found(3))

            repository.search(CHAT, ChatSearchQuery("", MessageSender.User(USER), MessageTopic.Forum(TOPIC)))

            val sent = client.sentOf<TdApi.SearchChatMessages>().single()
            assertEquals("", sent.query)
            assertEquals(USER, (sent.senderId as TdApi.MessageSenderUser).userId)
            assertEquals(TOPIC, (sent.topicId as TdApi.MessageTopicForum).forumTopicId)
        }

    @Test
    fun `an empty query finds nothing without asking TDLib`() =
        runTest {
            assertEquals(Outcome.Success(emptyPage()), repository.search(CHAT, ChatSearchQuery("   ")))
            assertEquals(
                Outcome.Success(emptyPage()),
                repository.search(CHAT, ChatSearchQuery("", MessageSender.User(USER), isSecretChat = true)),
            )
            assertTrue(client.sent.isEmpty())
        }

    @Test
    fun `a query TDLib cannot search finds nothing, other failures are errors`() =
        runTest {
            client.on(TdApi.SearchChatMessages::class, TdResult.Failure(TdError(400, "QUERY_TOO_SHORT")))
            assertEquals(Outcome.Success(emptyPage()), repository.search(CHAT, ChatSearchQuery("a")))

            client.on(TdApi.SearchChatMessages::class, TdResult.Failure(TdError(429, "FLOOD_WAIT_12")))
            assertEquals(Outcome.Failure(DomainError.FloodWait(12)), repository.search(CHAT, ChatSearchQuery("ab")))
        }

    @Test
    fun `searches a secret chat on the device, paged by TDLib's offset`() =
        runTest {
            client.on(
                TdApi.SearchSecretMessages::class,
                TdResult.Success(TdApi.FoundMessages(-1, arrayOf(message(id = 8, chatId = CHAT)), "next")),
            )

            val first = repository.search(CHAT, ChatSearchQuery("cats", isSecretChat = true))
            repository.search(CHAT, ChatSearchQuery("cats", isSecretChat = true), ChatSearchCursor.Offset("next"))

            assertEquals(
                Outcome.Success(ChatSearchPage(persistentListOf(8L), null, ChatSearchCursor.Offset("next"))),
                first,
            )
            val sent = client.sentOf<TdApi.SearchSecretMessages>()
            assertEquals(listOf("", "next"), sent.map { it.offset })
            assertEquals(CHAT, sent.first().chatId)
            assertEquals("cats", sent.first().query)
            assertTrue(client.sentOf<TdApi.SearchChatMessages>().isEmpty())
        }

    @Test
    fun `a day shows its first message, the one after the last message before it`() =
        runTest {
            client.on(TdApi.GetChatMessageByDate::class, TdResult.Success(message(id = 20, chatId = CHAT)))
            client.on(
                TdApi.GetChatHistory::class,
                TdResult.Success(TdApi.Messages(2, arrayOf(message(id = 30), message(id = 20)))),
            )

            assertEquals(Outcome.Success(30L), repository.messageForDay(CHAT, START, END))
            assertEquals(START.epochSecond.toInt() - 1, client.sentOf<TdApi.GetChatMessageByDate>().single().date)
            val history = client.sentOf<TdApi.GetChatHistory>().single()
            assertEquals(20L, history.fromMessageId)
            assertEquals(-1, history.offset)
            assertEquals(2, history.limit)
        }

    @Test
    fun `a day after the newest message shows the newest one`() =
        runTest {
            client.on(TdApi.GetChatMessageByDate::class, TdResult.Success(message(id = 20, chatId = CHAT)))
            client.on(TdApi.GetChatHistory::class, TdResult.Success(TdApi.Messages(1, arrayOf(message(id = 20)))))

            assertEquals(Outcome.Success(20L), repository.messageForDay(CHAT, START, END))
        }

    @Test
    fun `without a message before the day, the last one up to its end, and without one either, not found`() =
        runTest {
            client.on(TdApi.GetChatMessageByDate::class) { request ->
                if (request.date < START.epochSecond) {
                    TdResult.Failure(TdError(404, "Not Found"))
                } else {
                    TdResult.Success(message(id = 12, chatId = CHAT))
                }
            }
            assertEquals(Outcome.Success(12L), repository.messageForDay(CHAT, START, END))
            assertEquals(END.epochSecond.toInt() - 1, client.sentOf<TdApi.GetChatMessageByDate>().last().date)

            client.on(TdApi.GetChatMessageByDate::class, TdResult.Failure(TdError(404, "Not Found")))
            assertEquals(Outcome.Failure(DomainError.NotFound), repository.messageForDay(CHAT, START, END))
        }

    @Test
    fun `a failure to find a day's message is an error`() =
        runTest {
            client.on(TdApi.GetChatMessageByDate::class, TdResult.Failure(TdError(401, "Unauthorized")))

            assertEquals(Outcome.Failure(DomainError.Unauthorized), repository.messageForDay(CHAT, START, END))
        }

    private fun emptyPage() = ChatSearchPage(persistentListOf(), 0, null)

    private companion object {
        const val CHAT = -100L
        const val USER = 42L
        const val TOPIC = 7
        val START: Instant = Instant.parse("2026-10-01T00:00:00Z")
        val END: Instant = Instant.parse("2026-10-02T00:00:00Z")
    }
}
