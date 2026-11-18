package dev.kyralabs.pingogram.feature.settings.ui.snippets

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Snippet
import dev.kyralabs.pingogram.core.testing.FakeSnippetRepository
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Snippets: the saved texts in their order, moved by a drag or a screen reader's move, a failed save said. */
@OptIn(ExperimentalCoroutinesApi::class)
class SnippetsViewModelTest {
    private val repository = FakeSnippetRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository.saved.value = listOf(ADDRESS, THANKS, IBAN)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): SnippetsViewModel =
        SnippetsViewModel(repository).also {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
            runCurrent()
        }

    private fun SnippetsViewModel.ids() = (uiState.value as SnippetsUiState.Content).snippets.map { it.id }

    @Test
    fun `the page shows the snippets in the user's order`() =
        runTest {
            val viewModel = viewModel()

            assertEquals(listOf(ADDRESS, THANKS, IBAN), (viewModel.uiState.value as SnippetsUiState.Content).snippets)
        }

    @Test
    fun `a drag saves the order it leaves`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(SnippetsEvent.Reorder(listOf(IBAN.id, ADDRESS.id, THANKS.id)))
            runCurrent()

            assertEquals(listOf(IBAN.id, ADDRESS.id, THANKS.id), viewModel.ids())
        }

    @Test
    fun `a screen reader moves a snippet one place, never past the ends`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(SnippetsEvent.Move(THANKS.id, up = true))
            runCurrent()
            viewModel.onEvent(SnippetsEvent.Move(THANKS.id, up = true))
            runCurrent()
            viewModel.onEvent(SnippetsEvent.Move(ADDRESS.id, up = false))
            runCurrent()

            assertEquals(listOf(THANKS.id, IBAN.id, ADDRESS.id), viewModel.ids())
        }

    @Test
    fun `an order that cannot be saved says so and stays`() =
        runTest {
            val viewModel = viewModel()
            val effects = mutableListOf<SnippetsEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
            repository.failure = DomainError.Unknown(code = 0, message = "Disk full")

            viewModel.onEvent(SnippetsEvent.Move(IBAN.id, up = true))
            runCurrent()

            assertEquals(listOf(SnippetsEffect.ShowError(DomainError.Unknown(0, "Disk full"))), effects)
            assertEquals(listOf(ADDRESS.id, THANKS.id, IBAN.id), viewModel.ids())
        }

    private companion object {
        val ADDRESS = Snippet(1, "address", "12 Crescent Road")
        val THANKS = Snippet(2, "thanks", "Thank you!")
        val IBAN = Snippet(3, "iban", "GB33 BUKB 2020 1555 5555 55")
    }
}
