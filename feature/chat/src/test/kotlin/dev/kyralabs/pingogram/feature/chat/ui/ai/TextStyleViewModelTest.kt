package dev.kyralabs.pingogram.feature.chat.ui.ai

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A shared AI style's sheet ([TextStyleViewModel]). */
@OptIn(ExperimentalCoroutinesApi::class)
class TextStyleViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val ai = FakeAiText()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): TextStyleViewModel {
        val viewModel = TextStyleViewModel(SavedStateHandle(mapOf("name" to "pirate")), ai)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TextStyleViewModel.content() = uiState.value as TextStyleUiState.Content

    @Test
    fun `the style shows with the account's limit, and nothing is added before the tap`() =
        runTest(dispatcher) {
            val content = viewModel().content()

            assertEquals("Pirate", content.style.title)
            assertEquals(3, content.limit)
            assertFalse(content.isAdded)
            assertTrue(ai.added.isEmpty())
        }

    @Test
    fun `adding adds the style, which then shows as added`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.onEvent(TextStyleEvent.Add)
            runCurrent()

            assertEquals(listOf("pirate"), ai.added)
            assertTrue(viewModel.content().isAdded)
        }

    @Test
    fun `Telegram's limit is shown, the style not added`() =
        runTest(dispatcher) {
            ai.addResult = Outcome.Failure(DomainError.TooManyTextStyles)
            val viewModel = viewModel()

            viewModel.onEvent(TextStyleEvent.Add)
            runCurrent()

            assertEquals(DomainError.TooManyTextStyles, viewModel.content().error)
            assertFalse(viewModel.content().isAdded)
        }

    @Test
    fun `a style that is not found says so, and can be asked for again`() =
        runTest(dispatcher) {
            ai.found = Outcome.Failure(DomainError.NotFound)
            val viewModel = viewModel()
            assertEquals(TextStyleUiState.Failed(DomainError.NotFound), viewModel.uiState.value)

            ai.found =
                Outcome.Success(
                    dev.kyralabs.pingogram.core.domain.model
                        .TextCompositionStyle("pirate", "Pirate"),
                )
            viewModel.onEvent(TextStyleEvent.Retry)
            runCurrent()

            assertEquals("Pirate", viewModel.content().style.title)
        }

    @Test
    fun `removing takes it out of the user's styles`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.onEvent(TextStyleEvent.Add)
            runCurrent()

            viewModel.onEvent(TextStyleEvent.Remove)
            runCurrent()

            assertEquals(listOf("pirate"), ai.removed)
            assertFalse(viewModel.content().isAdded)
        }
}
