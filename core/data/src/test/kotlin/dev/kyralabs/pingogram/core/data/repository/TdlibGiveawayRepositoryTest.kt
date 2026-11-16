package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.GiveawayInfo
import dev.kyralabs.pingogram.core.domain.model.GiveawayParticipantStatus
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Asking TDLib what it knows of a giveaway: one request, its answer or its refusal. */
class TdlibGiveawayRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibGiveawayRepository(client)

    @Test
    fun `the info comes from getGiveawayInfo of the message`() =
        runTest {
            client.on(
                TdApi.GetGiveawayInfo::class,
                TdResult.Success(
                    TdApi.GiveawayInfoOngoing(1_764_000_000, TdApi.GiveawayParticipantStatusEligible(), false),
                ),
            )

            val result = repository.info(CHAT, MESSAGE)

            assertEquals(
                Outcome.Success(GiveawayInfo.Ongoing(1_764_000_000L, GiveawayParticipantStatus.Eligible, false)),
                result,
            )
            val sent = client.sentOf<TdApi.GetGiveawayInfo>().single()
            assertEquals(CHAT to MESSAGE, sent.chatId to sent.messageId)
        }

    @Test
    fun `a refusal comes back as its error`() =
        runTest {
            client.on(TdApi.GetGiveawayInfo::class, TdResult.Failure(TdError(400, "MESSAGE_ID_INVALID")))

            assertEquals(
                Outcome.Failure(DomainError.Unknown(400, "MESSAGE_ID_INVALID")),
                repository.info(CHAT, MESSAGE),
            )
        }

    private companion object {
        const val CHAT = -100L
        const val MESSAGE = 7L
    }
}
