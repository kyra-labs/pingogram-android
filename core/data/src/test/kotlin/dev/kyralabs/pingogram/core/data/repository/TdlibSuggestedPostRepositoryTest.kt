package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** An administrator's answers to a post suggested to a channel, each one request to TDLib. */
class TdlibSuggestedPostRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibSuggestedPostRepository(client)

    @Test
    fun `approving publishes it at the time its author asked`() =
        runTest {
            client.on(TdApi.ApproveSuggestedPost::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.approve(CHAT, MESSAGE))

            val sent = client.sentOf<TdApi.ApproveSuggestedPost>().single()
            assertEquals(Triple(CHAT, MESSAGE, 0), Triple(sent.chatId, sent.messageId, sent.sendDate))
        }

    @Test
    fun `declining sends the comment, trimmed`() =
        runTest {
            client.on(TdApi.DeclineSuggestedPost::class, TdResult.Success(TdApi.Ok()))

            repository.decline(CHAT, MESSAGE, "  Too long ")

            assertEquals("Too long", client.sentOf<TdApi.DeclineSuggestedPost>().single().comment)
        }

    @Test
    fun `a refusal comes back as its error`() =
        runTest {
            client.on(TdApi.ApproveSuggestedPost::class, TdResult.Failure(TdError(400, "SUGGESTED_POST_INVALID")))

            assertEquals(
                Outcome.Failure(DomainError.Unknown(400, "SUGGESTED_POST_INVALID")),
                repository.approve(CHAT, MESSAGE),
            )
        }

    private companion object {
        const val CHAT = -1001L
        const val MESSAGE = 7L
    }
}
