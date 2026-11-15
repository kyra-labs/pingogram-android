package dev.kyralabs.pingogram.feature.media.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.testing.FakeViewerHintsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** "Tap to show controls": once on this phone, the first time the controls are hidden. */
@OptIn(ExperimentalCoroutinesApi::class)
class ControlsHintViewModelTest {
    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the first time the controls are hidden the hint shows, then never again`() =
        runTest {
            val repository = FakeViewerHintsRepository()
            val viewModel = ControlsHintViewModel(repository)

            viewModel.hints.test {
                viewModel.onControlsHidden()
                awaitItem()
                viewModel.onControlsHidden()
                expectNoEvents()
            }
            assertTrue(repository.controlsHintShown.value)

            ControlsHintViewModel(repository).hints.test {
                expectNoEvents()
            }
        }

    @Test
    fun `a hint shown before does not show`() =
        runTest {
            val viewModel = ControlsHintViewModel(FakeViewerHintsRepository(shown = true))

            viewModel.hints.test {
                viewModel.onControlsHidden()
                expectNoEvents()
            }
        }
}
