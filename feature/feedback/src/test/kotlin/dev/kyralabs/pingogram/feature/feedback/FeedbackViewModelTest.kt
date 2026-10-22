package dev.kyralabs.pingogram.feature.feedback

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.domain.model.DiagnosticReport
import dev.kyralabs.pingogram.core.domain.repository.DiagnosticsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** "Send feedback": the report, what the user writes, and what is shared only when the user asks. */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackViewModelTest {
    private val diagnostics = FakeDiagnosticsRepository()
    private val created = mutableListOf<FeedbackViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        FeedbackViewModel(diagnostics, handle).also { created += it }

    @Test
    fun `loads, then shows the report with nothing written and the text hidden`() =
        runTest {
            viewModel().uiState.test {
                assertEquals(FeedbackUiState.Loading, awaitItem())
                val content = awaitItem() as FeedbackUiState.Content
                assertEquals(FeedbackSamples.report, content.report)
                assertEquals("", content.description)
                assertEquals(false, content.showReport)
                assertEquals(FeedbackSamples.report.toText(""), content.reportText)
            }
        }

    @Test
    fun `puts what the user writes into the report and keeps it through process death`() =
        runTest {
            val handle = SavedStateHandle()
            viewModel(handle).onEvent(FeedbackEvent.DescriptionChanged("It froze"))

            viewModel(handle).uiState.test {
                skipItems(1)
                val content = awaitItem() as FeedbackUiState.Content
                assertEquals("It froze", content.description)
                assertTrue(content.reportText.contains("What happened:\nIt froze"))
            }
        }

    @Test
    fun `shows and hides the report's text`() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                skipItems(2)
                viewModel.onEvent(FeedbackEvent.ToggleReport)
                assertEquals(true, (awaitItem() as FeedbackUiState.Content).showReport)
                viewModel.onEvent(FeedbackEvent.ToggleReport)
                assertEquals(false, (awaitItem() as FeedbackUiState.Content).showReport)
            }
        }

    @Test
    fun `shares, copies and opens the issue only when asked, with what the user wrote`() =
        runTest {
            val viewModel = viewModel()
            viewModel.effects.test {
                viewModel.onEvent(FeedbackEvent.DescriptionChanged("It froze"))
                testScheduler.runCurrent()
                expectNoEvents()
                val text = FeedbackSamples.report.toText("It froze")

                viewModel.onEvent(FeedbackEvent.Share)
                assertEquals(FeedbackEffect.Share(text), awaitItem())
                viewModel.onEvent(FeedbackEvent.Copy)
                assertEquals(FeedbackEffect.Copy(text), awaitItem())
                viewModel.onEvent(FeedbackEvent.OpenIssue)
                assertEquals(FeedbackEffect.OpenUrl(FeedbackSamples.report.issueUrl("It froze")), awaitItem())
            }
        }

    @Test
    fun `does nothing with the report before it is read`() =
        runTest {
            val viewModel = viewModel()
            viewModel.effects.test {
                viewModel.onEvent(FeedbackEvent.Share)
                expectNoEvents()
            }
        }

    @Test
    fun `deletes the kept crash and says so`() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                skipItems(2)
                viewModel.effects.test {
                    viewModel.onEvent(FeedbackEvent.DeleteCrash)
                    assertEquals(FeedbackEffect.CrashDeleted, awaitItem())
                }
                assertNull((awaitItem() as FeedbackUiState.Content).report.crash)
            }
            assertEquals(1, diagnostics.cleared)
        }

    private class FakeDiagnosticsRepository : DiagnosticsRepository {
        var cleared = 0

        override suspend fun report(): DiagnosticReport =
            if (cleared > 0) FeedbackSamples.report.copy(crash = null) else FeedbackSamples.report

        override suspend fun clearCrash() {
            cleared++
        }
    }
}
