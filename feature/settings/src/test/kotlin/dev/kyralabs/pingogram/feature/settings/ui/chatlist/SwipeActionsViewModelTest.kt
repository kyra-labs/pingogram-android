package dev.kyralabs.pingogram.feature.settings.ui.chatlist

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.repository.SwipeAction
import dev.kyralabs.pingogram.core.testing.FakeChatListSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The Swipe actions page on a fake repository: what it shows, saving each choice, and a failed save. */
@OptIn(ExperimentalCoroutinesApi::class)
class SwipeActionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeChatListSettingsRepository()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): SwipeActionsViewModel =
        SwipeActionsViewModel(repository).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            runCurrent()
        }

    private fun TestScope.send(
        viewModel: SwipeActionsViewModel,
        event: SwipeActionsEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    @Test
    fun `shows read and archive at first`() =
        runTest(dispatcher) {
            assertEquals(SwipeActionsUiState.Content(SwipeAction.Read, SwipeAction.Archive), viewModel().uiState.value)
        }

    @Test
    fun `a choice saves that side only and shows`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            send(viewModel, SwipeActionsEvent.Choose(SwipeSide.End, SwipeAction.Pin))
            send(viewModel, SwipeActionsEvent.Choose(SwipeSide.Start, SwipeAction.Delete))

            assertEquals(SwipeAction.Pin, repository.settings.value.startSwipe)
            assertEquals(SwipeAction.Delete, repository.settings.value.endSwipe)
            assertEquals(SwipeActionsUiState.Content(SwipeAction.Pin, SwipeAction.Delete), viewModel.uiState.value)
        }

    @Test
    fun `a choice that cannot be saved keeps what is saved, with a snackbar`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val effects = mutableListOf<SwipeActionsEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
            repository.failure = DomainError.Unknown(0, "Disk full")

            send(viewModel, SwipeActionsEvent.Choose(SwipeSide.Start, SwipeAction.None))

            assertEquals(SwipeActionsUiState.Content(SwipeAction.Read, SwipeAction.Archive), viewModel.uiState.value)
            assertTrue(effects.single() is SwipeActionsEffect.ShowError)
        }
}
