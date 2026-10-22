package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageThread
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
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

/** A message's thread: the discussion group's thread of a channel post, a group's replies, and when there is none. */
class TdlibMessageThreadRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibMessageThreadRepository(client)

    @Test
    fun `a channel post's thread is in its discussion group, the post as its head`() =
        runTest {
            val post =
                message(
                    id = THREAD,
                    chatId = GROUP,
                    forwardInfo = TdApi.MessageForwardInfo(TdApi.MessageOriginChannel(CHANNEL, 9, ""), 0, null, ""),
                )
            client.on(TdApi.GetMessageThread::class) {
                TdResult.Success(
                    TdApi.MessageThreadInfo(
                        GROUP,
                        THREAD,
                        TdApi.MessageReplyInfo(12, emptyArray(), 40, 38, 50),
                        3,
                        arrayOf(post),
                        null,
                    ),
                )
            }

            val thread = (repository.thread(CHANNEL, 9) as Outcome.Success).value

            val request = client.sentOf<TdApi.GetMessageThread>().single()
            assertEquals(CHANNEL, request.chatId)
            assertEquals(9L, request.messageId)
            assertEquals(GROUP, thread.chatId)
            assertEquals(MessageTopic.Thread(THREAD), thread.topic)
            assertEquals(listOf(THREAD), thread.head.map { it.id })
            assertEquals(12, thread.replyCount)
            assertEquals(3, thread.unreadCount)
            assertEquals(40L, thread.lastReadInboxMessageId)
            assertEquals(38L, thread.lastReadOutboxMessageId)
            assertTrue(thread.isComments)
        }

    @Test
    fun `an album's head is oldest first, and a group's thread is replies`() =
        runTest {
            client.on(TdApi.GetMessageThread::class) {
                TdResult.Success(
                    TdApi.MessageThreadInfo(
                        GROUP,
                        THREAD + 1,
                        null,
                        0,
                        arrayOf(message(id = THREAD + 1, chatId = GROUP), message(id = THREAD, chatId = GROUP)),
                        null,
                    ),
                )
            }

            val thread: MessageThread = (repository.thread(GROUP, THREAD) as Outcome.Success).value

            assertEquals(listOf(THREAD, THREAD + 1), thread.head.map { it.id })
            assertEquals(0, thread.replyCount)
            assertFalse(thread.isComments)
        }

    @Test
    fun `a message without a thread is not found`() =
        runTest {
            client.on(TdApi.GetMessageThread::class) { TdResult.Failure(TdError(400, "Message thread not found")) }

            val outcome = repository.thread(CHANNEL, 9)

            assertEquals(DomainError.NotFound, (outcome as Outcome.Failure).error)
        }

    private companion object {
        const val CHANNEL = -1001L
        const val GROUP = -1002L
        const val THREAD = 30L
    }
}
