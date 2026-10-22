package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatBoostStatus
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Boosts, read only: `getChatBoostStatus` and the count of the user's unused boosts, never `boostChat`. */
class TdlibChatBoostRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibChatBoostRepository(client)

    private fun status(appliedSlotIds: IntArray = intArrayOf()) =
        TdApi.ChatBoostStatus("https://t.me/news?boost", appliedSlotIds, 2, 0, 14, 10, 20, 30, 1.5, emptyArray())

    @Test
    fun `maps the level and the boosts, with the user's unused boosts`() =
        runTest {
            client.on(TdApi.GetChatBoostStatus::class, TdResult.Success(status(intArrayOf(3))))
            client.on(
                TdApi.GetAvailableChatBoostSlots::class,
                TdResult.Success(
                    TdApi.ChatBoostSlots(
                        arrayOf(TdApi.ChatBoostSlot(3, 9, 0, 0, 0), TdApi.ChatBoostSlot(4, 0, 0, 0, 0)),
                    ),
                ),
            )

            assertEquals(
                Outcome.Success(
                    ChatBoostStatus(
                        level = 2,
                        boostCount = 14,
                        currentLevelBoostCount = 10,
                        nextLevelBoostCount = 20,
                        premiumMemberCount = 30,
                        premiumMemberPercentage = 1.5,
                        boostedByUser = true,
                        unusedBoosts = 1,
                    ),
                ),
                repository.status(9),
            )
            assertEquals(9L, client.sentOf<TdApi.GetChatBoostStatus>().single().chatId)
            assertTrue(client.sentOf<TdApi.BoostChat>().isEmpty())
        }

    @Test
    fun `boost slots that cannot be read count as none`() =
        runTest {
            client.on(TdApi.GetChatBoostStatus::class, TdResult.Success(status()))
            client.on(
                TdApi.GetAvailableChatBoostSlots::class,
                TdResult.Failure(TdError(400, "PREMIUM_ACCOUNT_REQUIRED")),
            )

            val status = (repository.status(9) as Outcome.Success).value

            assertEquals(0, status.unusedBoosts)
            assertEquals(false, status.boostedByUser)
        }

    @Test
    fun `progress and the boosts to the next level come from the counts`() {
        val status = ChatBoostStatus(2, 14, 10, 20, 0, 0.0)

        assertEquals(0.4f, status.levelProgress)
        assertEquals(6, status.boostsToNextLevel)
        assertEquals(1f, status.copy(nextLevelBoostCount = 0).levelProgress)
        assertEquals(0, status.copy(nextLevelBoostCount = 0).boostsToNextLevel)
    }

    @Test
    fun `a failure of the status is the outcome`() =
        runTest {
            client.on(TdApi.GetChatBoostStatus::class, TdResult.Failure(TdError(500, "Network is unreachable")))

            assertEquals(Outcome.Failure(DomainError.Network), repository.status(9))
        }
}
