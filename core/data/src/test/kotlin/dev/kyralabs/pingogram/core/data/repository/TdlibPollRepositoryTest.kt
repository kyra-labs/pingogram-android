package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.PollVoter
import dev.kyralabs.pingogram.core.domain.model.PollVoters
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Voting, taking a vote back, stopping a poll and listing its voters, each one request to TDLib. */
class TdlibPollRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibPollRepository(client)

    @Test
    fun `a vote sends the chosen options' indexes`() =
        runTest {
            client.on(TdApi.SetPollAnswer::class, TdResult.Success(TdApi.Ok()))

            val result = repository.vote(CHAT, MESSAGE, listOf(0, 2))

            assertEquals(Outcome.Success(Unit), result)
            val sent = client.sentOf<TdApi.SetPollAnswer>().single()
            assertEquals(CHAT to MESSAGE, sent.chatId to sent.messageId)
            assertArrayEquals(intArrayOf(0, 2), sent.optionIds)
        }

    @Test
    fun `taking a vote back sends no options`() =
        runTest {
            client.on(TdApi.SetPollAnswer::class, TdResult.Success(TdApi.Ok()))

            repository.vote(CHAT, MESSAGE, emptyList())

            assertArrayEquals(intArrayOf(), client.sentOf<TdApi.SetPollAnswer>().single().optionIds)
        }

    @Test
    fun `a vote in a poll closed meanwhile fails with PollClosed`() =
        runTest {
            client.on(TdApi.SetPollAnswer::class, TdResult.Failure(TdError(400, "MESSAGE_POLL_CLOSED")))

            assertEquals(Outcome.Failure(DomainError.PollClosed), repository.vote(CHAT, MESSAGE, listOf(1)))
        }

    @Test
    fun `a flood wait and a refused session come back as their errors`() =
        runTest {
            client.on(TdApi.SetPollAnswer::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 9")))
            client.on(TdApi.StopPoll::class, TdResult.Failure(TdError(401, "Unauthorized")))

            assertEquals(Outcome.Failure(DomainError.FloodWait(9)), repository.vote(CHAT, MESSAGE, listOf(0)))
            assertEquals(Outcome.Failure(DomainError.Unauthorized), repository.stop(CHAT, MESSAGE))
        }

    @Test
    fun `a vote TDLib does not answer in time fails with Network`() =
        runTest {
            client.on(TdApi.SetPollAnswer::class) { awaitCancellation() }

            assertEquals(Outcome.Failure(DomainError.Network), repository.vote(CHAT, MESSAGE, listOf(0)))
        }

    @Test
    fun `stopping sends the message without a reply markup`() =
        runTest {
            client.on(TdApi.StopPoll::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.stop(CHAT, MESSAGE))
            val sent = client.sentOf<TdApi.StopPoll>().single()
            assertEquals(CHAT to MESSAGE, sent.chatId to sent.messageId)
            assertNull(sent.replyMarkup)
        }

    @Test
    fun `voters come in pages of an option, users and chats with when they voted`() =
        runTest {
            client.on(
                TdApi.GetPollVoters::class,
                TdResult.Success(
                    TdApi.PollVoters(
                        12,
                        arrayOf(
                            TdApi.PollVoter(TdApi.MessageSenderUser(42), 1_700_000_000),
                            TdApi.PollVoter(TdApi.MessageSenderChat(-100L), 0),
                        ),
                    ),
                ),
            )

            val voters = repository.voters(CHAT, MESSAGE, optionIndex = 1, offset = 20, limit = 20)

            assertEquals(
                Outcome.Success(
                    PollVoters(
                        totalCount = 12,
                        voters =
                            persistentListOf(
                                PollVoter(MessageSender.User(42), 1_700_000_000),
                                PollVoter(MessageSender.Chat(-100L), 0),
                            ),
                    ),
                ),
                voters,
            )
            val sent = client.sentOf<TdApi.GetPollVoters>().single()
            assertEquals(
                listOf(CHAT, MESSAGE, 1L, 20L, 20L),
                listOf(sent.chatId, sent.messageId, sent.optionId.toLong(), sent.offset.toLong(), sent.limit.toLong()),
            )
        }

    private companion object {
        const val CHAT = 100L
        const val MESSAGE = 7L
    }
}
