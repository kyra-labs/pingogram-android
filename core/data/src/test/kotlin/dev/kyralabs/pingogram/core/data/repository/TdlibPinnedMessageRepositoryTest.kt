package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageText
import dev.kyralabs.pingogram.core.testing.inTopic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The pinned messages of a chat: loaded once per collection, then kept current by the chat's updates. */
class TdlibPinnedMessageRepositoryTest {
    private val client = FakeTdlibClient()

    private val repository = TdlibPinnedMessageRepository(client)

    private fun answerPinned(vararg ids: Long) =
        client.on(
            TdApi.SearchChatMessages::class,
            TdResult.Success(TdApi.FoundChatMessages(ids.size, ids.map { pinned(it) }.toTypedArray(), 0)),
        )

    private fun pinned(id: Long) = message(id = id, chatId = CHAT, isPinned = true)

    @Test
    fun `loads the newest pinned messages of the chat, newest first`() =
        runTest {
            answerPinned(3, 9, 5)

            repository.pinnedMessages(CHAT).test {
                assertEquals(listOf(9L, 5L, 3L), awaitItem().map { it.id })
            }
            val search = client.sentOf<TdApi.SearchChatMessages>().single()
            assertEquals(CHAT, search.chatId)
            assertEquals(100, search.limit)
            assertTrue(search.filter is TdApi.SearchMessagesFilterPinned)
        }

    @Test
    fun `adds a message pinned later, fetched from TDLib, and removes one unpinned`() =
        runTest {
            answerPinned(3)
            client.on(TdApi.GetMessage::class) { TdResult.Success(pinned(it.messageId)) }

            repository.pinnedMessages(CHAT).test {
                assertEquals(listOf(3L), awaitItem().map { it.id })

                client.emit(TdApi.UpdateMessageIsPinned(CHAT, 7, true))
                assertEquals(listOf(7L, 3L), awaitItem().map { it.id })

                client.emit(TdApi.UpdateMessageIsPinned(CHAT, 3, false))
                assertEquals(listOf(7L), awaitItem().map { it.id })
            }
        }

    @Test
    fun `applies the updates that arrive while it loads`() =
        runTest {
            val answer = CompletableDeferred<TdResult<TdApi.FoundChatMessages>>()
            client.on(TdApi.SearchChatMessages::class) { answer.await() }

            repository.pinnedMessages(CHAT).test {
                client.emit(TdApi.UpdateMessageIsPinned(CHAT, 3, false))
                answer.complete(TdResult.Success(TdApi.FoundChatMessages(1, arrayOf(pinned(3)), 0)))

                assertEquals(listOf(3L), awaitItem().map { it.id })
                assertEquals(emptyList<Long>(), awaitItem().map { it.id })
            }
        }

    @Test
    fun `drops deleted messages and keeps the previews of edited ones current`() =
        runTest {
            answerPinned(3, 5)

            repository.pinnedMessages(CHAT).test {
                awaitItem()

                client.emit(TdApi.UpdateMessageContent(CHAT, 5, messageText("Edited")))
                val edited = awaitItem().first().content as MessageContent.Text
                assertEquals("Edited", edited.text.text)

                // Only unloaded from TDLib's memory: still pinned.
                client.emit(TdApi.UpdateDeleteMessages(CHAT, longArrayOf(3), false, true))
                client.emit(TdApi.UpdateDeleteMessages(CHAT, longArrayOf(5), true, false))
                assertEquals(listOf(3L), awaitItem().map { it.id })
            }
        }

    @Test
    fun `ignores other chats, and a message no longer pinned once fetched`() =
        runTest {
            answerPinned()
            client.on(TdApi.GetMessage::class) { TdResult.Success(message(id = it.messageId, chatId = CHAT)) }

            repository.pinnedMessages(CHAT).test {
                assertEquals(emptyList<Long>(), awaitItem().map { it.id })

                client.emit(TdApi.UpdateMessageIsPinned(OTHER_CHAT, 4, true))
                client.emit(TdApi.UpdateMessageIsPinned(CHAT, 7, true))
                expectNoEvents()
            }
            assertEquals(listOf(7L), client.sentOf<TdApi.GetMessage>().map { it.messageId })
        }

    @Test
    fun `the pins of a topic are searched there, and a new pin joins them only from the topic`() =
        runTest {
            answerPinned()
            client.on(TdApi.GetMessage::class) { request ->
                val topicId = if (request.messageId == 7L) TOPIC else TOPIC + 1
                TdResult.Success(
                    message(
                        id = request.messageId,
                        chatId = CHAT,
                        isPinned = true,
                    ).inTopic(TdApi.MessageTopicForum(topicId)),
                )
            }

            repository.pinnedMessages(CHAT, MessageTopic.Forum(TOPIC)).test {
                awaitItem()
                client.emit(TdApi.UpdateMessageIsPinned(CHAT, 8, true))
                client.emit(TdApi.UpdateMessageIsPinned(CHAT, 7, true))
                assertEquals(listOf(7L), awaitItem().map { it.id })
            }
            val search = client.sentOf<TdApi.SearchChatMessages>().single()
            assertEquals(TOPIC, (search.topicId as TdApi.MessageTopicForum).forumTopicId)
        }

    @Test
    fun `reads as none when loading fails`() =
        runTest {
            client.on(TdApi.SearchChatMessages::class, TdResult.Failure(TdError(400, "CHAT_INVALID")))

            repository.pinnedMessages(CHAT).test {
                assertTrue(awaitItem().isEmpty())
            }
        }

    @Test
    fun `unpins every message of the chat`() =
        runTest {
            client.on(TdApi.UnpinAllChatMessages::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.unpinAll(CHAT))
            assertEquals(CHAT, client.sentOf<TdApi.UnpinAllChatMessages>().single().chatId)
        }

    private companion object {
        const val CHAT = -100L
        const val OTHER_CHAT = 5L
        const val TOPIC = 4
    }
}
