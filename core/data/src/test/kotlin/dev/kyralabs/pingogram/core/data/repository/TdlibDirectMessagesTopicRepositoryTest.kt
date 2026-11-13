package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A channel's direct messages topics: the cache of `updateDirectMessagesChatTopic` by chat, loading and marking. */
class TdlibDirectMessagesTopicRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibDirectMessagesTopicRepository(client, backgroundScope)

    private fun update(
        id: Long,
        chatId: Long = CHAT,
        order: Long = id,
        sender: TdApi.MessageSender = TdApi.MessageSenderUser(id * 10),
        unread: Long = 0,
        paid: Boolean = false,
        lastMessage: TdApi.Message? = null,
    ) = TdApi.UpdateDirectMessagesChatTopic(
        TdApi.DirectMessagesChatTopic(chatId, id, sender, order, !paid, false, unread, 0, 0, 0, lastMessage, null),
    )

    @Test
    fun `lists a chat's announced topics by order, leaving unlisted ones and other chats' out`() =
        runTest {
            val repository = repository()

            client.emit(update(id = 1, order = 5))
            client.emit(update(id = 2, order = 9))
            client.emit(update(id = 3, order = 0))
            client.emit(update(id = 4, chatId = OTHER_CHAT))

            assertEquals(listOf(2L, 1L), repository.topics(CHAT).first().map { it.id })
        }

    @Test
    fun `maps the sender, unread count, paid state and last message, and follows a change`() =
        runTest {
            val repository = repository()

            client.emit(update(id = 1, sender = TdApi.MessageSenderChat(-500), paid = true, unread = 3))
            client.emit(update(id = 1, unread = 0, lastMessage = TdApiFixtures.message(id = 7, chatId = CHAT)))

            val topic = repository.topic(CHAT, 1).first()
            assertEquals(MessageSender.User(10), topic?.sender)
            assertEquals(0L, topic?.unreadCount)
            assertFalse(topic!!.isPaid)
            assertEquals(7L, topic.lastMessage?.id)
            assertEquals(MessageTopic.DirectMessages(1), topic.topic)
            assertNull(repository.topic(CHAT, 9).first())
        }

    @Test
    fun `forgets the topics when TDLib closes`() =
        runTest {
            val repository = repository()
            client.emit(update(id = 1))

            client.emit(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))

            assertTrue(repository.topics(CHAT).first().isEmpty())
        }

    @Test
    fun `loading more reports when every topic is loaded`() =
        runTest {
            val repository = repository()
            client.on(TdApi.LoadDirectMessagesChatTopics::class, TdResult.Success(TdApi.Ok()))
            assertEquals(Outcome.Success(true), repository.loadMore(CHAT))
            assertEquals(CHAT, client.sentOf<TdApi.LoadDirectMessagesChatTopics>().single().chatId)

            client.on(TdApi.LoadDirectMessagesChatTopics::class, TdResult.Failure(TdError(404, "Not Found")))
            assertEquals(Outcome.Success(false), repository.loadMore(CHAT))

            client.on(
                TdApi.LoadDirectMessagesChatTopics::class,
                TdResult.Failure(TdError(500, "Network is unreachable")),
            )
            assertEquals(Outcome.Failure(DomainError.Network), repository.loadMore(CHAT))
        }

    @Test
    fun `marking a topic unread sends its flag`() =
        runTest {
            client.on(TdApi.SetDirectMessagesChatTopicIsMarkedAsUnread::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository().setMarkedAsUnread(CHAT, 4, unread = true))

            val request = client.sentOf<TdApi.SetDirectMessagesChatTopicIsMarkedAsUnread>().single()
            assertEquals(listOf<Any>(CHAT, 4L, true), listOf(request.chatId, request.topicId, request.isMarkedAsUnread))
        }

    private companion object {
        const val CHAT = -1_000_000_000_777L
        const val OTHER_CHAT = -1_000_000_000_888L
    }
}
