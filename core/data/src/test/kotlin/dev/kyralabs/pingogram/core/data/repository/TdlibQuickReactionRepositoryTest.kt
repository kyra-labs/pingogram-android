package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TdlibQuickReactionRepositoryTest {
    private val client = FakeTdlibClient()

    @Test
    fun `holds the reaction TDLib announced, and nothing before`() =
        runTest {
            val repository = TdlibQuickReactionRepository(client, backgroundScope)
            runCurrent()
            assertNull(repository.quickReaction.value)

            client.emit(TdApi.UpdateDefaultReactionType(TdApi.ReactionTypeEmoji("❤")))
            runCurrent()
            assertEquals(ReactionType.Emoji("❤"), repository.quickReaction.value)

            client.emit(TdApi.UpdateDefaultReactionType(TdApi.ReactionTypeCustomEmoji(CUSTOM_EMOJI)))
            runCurrent()
            assertEquals(ReactionType.CustomEmoji(CUSTOM_EMOJI), repository.quickReaction.value)
        }

    @Test
    fun `sets the default reaction`() =
        runTest {
            client.on(TdApi.SetDefaultReactionType::class, TdResult.Success(TdApi.Ok()))
            val repository = TdlibQuickReactionRepository(client, backgroundScope)

            assertEquals(Outcome.Success(Unit), repository.setQuickReaction(ReactionType.Emoji("🔥")))

            val sent = client.sentOf<TdApi.SetDefaultReactionType>().single().reactionType
            assertEquals("🔥", (sent as TdApi.ReactionTypeEmoji).emoji)
        }

    @Test
    fun `the paid reaction is refused before TDLib`() =
        runTest {
            val repository = TdlibQuickReactionRepository(client, backgroundScope)

            assertTrue(repository.setQuickReaction(ReactionType.Paid) is Outcome.Failure)
            assertTrue(client.sentOf<TdApi.SetDefaultReactionType>().isEmpty())
        }

    @Test
    fun `logging out forgets the reaction`() =
        runTest {
            val repository = TdlibQuickReactionRepository(client, backgroundScope)
            runCurrent()
            client.emit(TdApi.UpdateDefaultReactionType(TdApi.ReactionTypeEmoji("❤")))
            client.emit(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))
            runCurrent()

            assertNull(repository.quickReaction.value)
        }

    private companion object {
        const val CUSTOM_EMOJI = 5_368_324_170_671_202_286L
    }
}
