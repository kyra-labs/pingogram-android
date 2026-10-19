package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BlockedSendersPage
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TdlibBlockListRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibBlockListRepository(client)

    private fun answerBlocked(
        totalCount: Int,
        vararg senders: TdApi.MessageSender,
    ) = client.on(
        TdApi.GetBlockedMessageSenders::class,
        TdResult.Success(TdApi.MessageSenders(totalCount, arrayOf(*senders))),
    )

    @Test
    fun `a page holds the blocked users and chats with the total count`() =
        runTest {
            answerBlocked(3, TdApi.MessageSenderUser(7), TdApi.MessageSenderChat(-100))

            assertEquals(
                Outcome.Success(
                    BlockedSendersPage(
                        totalCount = 3,
                        senders = persistentListOf(MessageSender.User(7), MessageSender.Chat(-100)),
                    ),
                ),
                repository.blockedSenders(offset = 0),
            )
        }

    @Test
    fun `asks for the main block list from the offset`() =
        runTest {
            answerBlocked(0)

            repository.blockedSenders(offset = 50, limit = 20)

            val request = client.sentOf<TdApi.GetBlockedMessageSenders>().single()
            assertInstanceOf(TdApi.BlockListMain::class.java, request.blockList)
            assertEquals(50, request.offset)
            assertEquals(20, request.limit)
        }

    @Test
    fun `a page has fifty users and chats by default`() =
        runTest {
            answerBlocked(0)

            repository.blockedSenders(offset = 0)

            assertEquals(50, client.sentOf<TdApi.GetBlockedMessageSenders>().single().limit)
        }

    @Test
    fun `loading a page maps errors`() =
        runTest {
            client.on(
                TdApi.GetBlockedMessageSenders::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 4")),
            )

            assertEquals(Outcome.Failure(DomainError.FloodWait(4)), repository.blockedSenders(offset = 0))
        }

    @Test
    fun `loading a page without an answer in time fails with a network error`() =
        runTest {
            client.on(
                TdApi.GetBlockedMessageSenders::class,
            ) { CompletableDeferred<TdResult<TdApi.MessageSenders>>().await() }

            val result = async { repository.blockedSenders(offset = 0) }
            advanceTimeBy(REQUEST_TIMEOUT)
            runCurrent()

            assertEquals(Outcome.Failure(DomainError.Network), result.await())
        }

    @Test
    fun `unblocking takes the sender off every block list`() =
        runTest {
            client.on(TdApi.SetMessageSenderBlockList::class, TdResult.Success(TdApi.Ok()))

            val result = repository.unblock(MessageSender.User(7))

            val request = client.sentOf<TdApi.SetMessageSenderBlockList>().single()
            assertEquals(7L, (request.senderId as TdApi.MessageSenderUser).userId)
            assertNull(request.blockList)
            assertEquals(Outcome.Success(Unit), result)
        }

    @Test
    fun `blocking puts the sender on the main block list`() =
        runTest {
            client.on(TdApi.SetMessageSenderBlockList::class, TdResult.Success(TdApi.Ok()))

            val result = repository.block(MessageSender.Chat(-100))

            val request = client.sentOf<TdApi.SetMessageSenderBlockList>().single()
            assertEquals(-100L, (request.senderId as TdApi.MessageSenderChat).chatId)
            assertInstanceOf(TdApi.BlockListMain::class.java, request.blockList)
            assertEquals(Outcome.Success(Unit), result)
        }

    @Test
    fun `unblocking maps errors`() =
        runTest {
            client.on(TdApi.SetMessageSenderBlockList::class, TdResult.Failure(TdError(500, "Network is unreachable")))

            assertEquals(Outcome.Failure(DomainError.Network), repository.unblock(MessageSender.User(7)))
        }
}
