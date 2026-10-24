package dev.kyralabs.pingogram.feature.settings.ui.reactions

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibChatSettingsRepository
import dev.kyralabs.pingogram.core.domain.model.DoubleTapReactions
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.testing.FakeDoubleTapSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeQuickReactionRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The double-tap page on fake repositories, and the emoji TDLib announces through the real settings repository. */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickReactionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val client = FakeTdlibClient()
    private val quickReactions = FakeQuickReactionRepository(ReactionType.Emoji("👍"))
    private val doubleTap = FakeDoubleTapSettingsRepository()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): QuickReactionViewModel {
        val chatSettings = TdlibChatSettingsRepository(client, backgroundScope)
        runCurrent()
        return QuickReactionViewModel(quickReactions, doubleTap, chatSettings).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            runCurrent()
        }
    }

    private suspend fun announceEmoji(vararg emojis: String) =
        client.emit(TdApi.UpdateActiveEmojiReactions(arrayOf(*emojis)))

    private val QuickReactionViewModel.content get() = uiState.value as QuickReactionUiState.Content

    @Test
    fun `loads until TDLib announced the emoji, then shows them with the quick reaction chosen`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            assertEquals(QuickReactionUiState.Loading, viewModel.uiState.value)

            announceEmoji("👍", "❤", "🔥")
            runCurrent()

            assertEquals(
                QuickReactionUiState.Content(
                    reactions = persistentListOf("👍", "❤", "🔥"),
                    chosen = "👍",
                    isCustomEmoji = false,
                    doubleTap = DoubleTapReactions(),
                ),
                viewModel.uiState.value,
            )
        }

    @Test
    fun `a custom emoji set in another app is no choice here`() =
        runTest(dispatcher) {
            quickReactions.announced.value = ReactionType.CustomEmoji(CUSTOM_EMOJI)
            val viewModel = viewModel()
            announceEmoji("👍", "❤")
            runCurrent()

            assertEquals(null, viewModel.content.chosen)
            assertTrue(viewModel.content.isCustomEmoji)
        }

    @Test
    fun `choosing an emoji sends it, and what TDLib announces shows`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            announceEmoji("👍", "❤")
            runCurrent()

            viewModel.onEvent(QuickReactionEvent.Choose("❤"))
            runCurrent()

            assertEquals(listOf<ReactionType>(ReactionType.Emoji("❤")), quickReactions.requested)
            assertEquals("❤", viewModel.content.chosen)
        }

    @Test
    fun `a choice TDLib took but has not announced yet leaves what it announced`() =
        runTest(dispatcher) {
            quickReactions.announces = false
            val viewModel = viewModel()
            announceEmoji("👍", "❤")
            runCurrent()

            viewModel.onEvent(QuickReactionEvent.Choose("❤"))
            runCurrent()
            assertEquals("👍", viewModel.content.chosen)

            quickReactions.announced.value = ReactionType.Emoji("❤")
            runCurrent()
            assertEquals("❤", viewModel.content.chosen)
        }

    @Test
    fun `choosing the quick reaction it has sends nothing`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            announceEmoji("👍", "❤")
            runCurrent()

            viewModel.onEvent(QuickReactionEvent.Choose("👍"))
            runCurrent()

            assertTrue(quickReactions.requested.isEmpty())
        }

    @Test
    fun `a refusal takes the choice back with a snackbar`() =
        runTest(dispatcher) {
            quickReactions.failure = DomainError.Network
            val viewModel = viewModel()
            announceEmoji("👍", "❤")
            runCurrent()

            viewModel.effects.test {
                viewModel.onEvent(QuickReactionEvent.Choose("❤"))
                runCurrent()

                assertEquals(QuickReactionEffect.ShowError(DomainError.Network), awaitItem())
            }
            assertEquals("👍", viewModel.content.chosen)
        }

    @Test
    fun `the switches save where a double tap reacts`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            announceEmoji("👍")
            runCurrent()

            viewModel.onEvent(QuickReactionEvent.SetOutgoing(false))
            runCurrent()
            assertEquals(DoubleTapReactions(incoming = true, outgoing = false), doubleTap.doubleTap.first())
            assertEquals(DoubleTapReactions(incoming = true, outgoing = false), viewModel.content.doubleTap)

            viewModel.onEvent(QuickReactionEvent.SetIncoming(false))
            runCurrent()
            assertEquals(DoubleTapReactions(incoming = false, outgoing = false), viewModel.content.doubleTap)
        }

    @Test
    fun `a switch that cannot be saved is a snackbar`() =
        runTest(dispatcher) {
            doubleTap.failure = DomainError.Unknown(code = 0, message = "Disk full")
            val viewModel = viewModel()
            announceEmoji("👍")
            runCurrent()

            viewModel.effects.test {
                viewModel.onEvent(QuickReactionEvent.SetIncoming(false))
                runCurrent()

                assertTrue((awaitItem() as QuickReactionEffect.ShowError).error is DomainError.Unknown)
            }
            assertEquals(DoubleTapReactions(), viewModel.content.doubleTap)
        }

    private companion object {
        const val CUSTOM_EMOJI = 5_368_324_170_671_202_286L
    }
}
