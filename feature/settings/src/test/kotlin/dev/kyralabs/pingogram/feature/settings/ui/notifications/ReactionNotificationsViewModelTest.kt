package dev.kyralabs.pingogram.feature.settings.ui.notifications

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ReactionNotificationSettings
import dev.kyralabs.pingogram.core.domain.model.ReactionNotificationSource
import dev.kyralabs.pingogram.core.domain.repository.ReactionNotificationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The reactions page on a fake repository, which TDLib's announcements fill. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReactionNotificationsViewModelTest {
    private val announced =
        ReactionNotificationSettings(
            messages = ReactionNotificationSource.All,
            stories = ReactionNotificationSource.All,
            pollVotes = ReactionNotificationSource.All,
            showPreview = true,
        )

    private class FakeReactions(
        initial: ReactionNotificationSettings?,
    ) : ReactionNotificationRepository {
        override val settings = MutableStateFlow(initial)
        val sent = mutableListOf<ReactionNotificationSettings>()
        var answer = CompletableDeferred<Outcome<Unit>>()

        override suspend fun setSettings(settings: ReactionNotificationSettings): Outcome<Unit> {
            sent += settings
            return answer.await()
        }
    }

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.collected(state: StateFlow<ReactionNotificationsUiState>) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect {} }

    @Test
    fun `shows nothing until TDLib announced the settings`() =
        runTest(dispatcher) {
            val repository = FakeReactions(initial = null)
            val viewModel = ReactionNotificationsViewModel(repository)
            collected(viewModel.uiState)
            runCurrent()
            assertEquals(ReactionNotificationsUiState(), viewModel.uiState.value)

            repository.settings.value = announced
            runCurrent()

            assertEquals(announced, viewModel.uiState.value.settings)
        }

    @Test
    fun `a choice shows at once and goes to Telegram with the other settings kept`() =
        runTest(dispatcher) {
            val repository = FakeReactions(announced)
            val viewModel = ReactionNotificationsViewModel(repository)
            collected(viewModel.uiState)
            runCurrent()

            viewModel.onEvent(
                ReactionNotificationsEvent.Choose(ReactionNotificationKind.Stories, ReactionNotificationSource.None),
            )
            runCurrent()

            val chosen = announced.copy(stories = ReactionNotificationSource.None)
            assertEquals(chosen, viewModel.uiState.value.settings)
            assertEquals(listOf(chosen), repository.sent)

            repository.settings.value = chosen
            repository.answer.complete(Outcome.Success(Unit))
            runCurrent()
            assertEquals(chosen, viewModel.uiState.value.settings)
        }

    @Test
    fun `a refused change goes back, with a snackbar`() =
        runTest(dispatcher) {
            val repository = FakeReactions(announced)
            val viewModel = ReactionNotificationsViewModel(repository)
            collected(viewModel.uiState)
            runCurrent()

            viewModel.onEvent(ReactionNotificationsEvent.SetShowPreview(false))
            runCurrent()
            assertEquals(
                false,
                viewModel.uiState.value.settings
                    ?.showPreview,
            )

            repository.answer.complete(Outcome.Failure(DomainError.Network))
            runCurrent()

            assertEquals(announced, viewModel.uiState.value.settings)
            assertEquals(ReactionNotificationsEffect.ShowError(DomainError.Network), viewModel.effects.first())
        }
}
