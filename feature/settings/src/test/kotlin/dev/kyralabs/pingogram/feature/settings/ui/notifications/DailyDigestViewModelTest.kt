package dev.kyralabs.pingogram.feature.settings.ui.notifications

import dev.kyralabs.pingogram.core.domain.repository.DailyDigestSettings
import dev.kyralabs.pingogram.core.testing.FakeDailyDigestSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
import java.time.LocalTime

/** The Daily digest's settings (`REQUIREMENTS.md` FR-X23): what is saved shows, and what cannot be saved says so. */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyDigestViewModelTest {
    private val repository = FakeDailyDigestSettingsRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel() =
        DailyDigestViewModel(repository).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            runCurrent()
        }

    @Test
    fun `off at 8 30 at first, then on and at the time picked`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(DailyDigestSettings(), viewModel.uiState.value.settings)

            viewModel.onEvent(DailyDigestEvent.SetEnabled(true))
            viewModel.onEvent(DailyDigestEvent.PickTime)
            assertTrue(viewModel.uiState.value.isPickingTime)
            viewModel.onEvent(DailyDigestEvent.SetTime(LocalTime.of(7, 45)))
            runCurrent()

            assertEquals(
                DailyDigestUiState(DailyDigestSettings(enabled = true, time = LocalTime.of(7, 45))),
                viewModel.uiState.value,
            )
        }

    @Test
    fun `the picker closes without a time, and a setting not saved is a snackbar`() =
        runTest {
            val viewModel = viewModel()
            val effects = mutableListOf<DailyDigestEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
            repository.fails = true

            viewModel.onEvent(DailyDigestEvent.PickTime)
            viewModel.onEvent(DailyDigestEvent.DismissTime)
            viewModel.onEvent(DailyDigestEvent.SetEnabled(true))
            runCurrent()

            assertEquals(DailyDigestUiState(DailyDigestSettings()), viewModel.uiState.value)
            assertTrue(effects.single() is DailyDigestEffect.ShowError)
        }
}
