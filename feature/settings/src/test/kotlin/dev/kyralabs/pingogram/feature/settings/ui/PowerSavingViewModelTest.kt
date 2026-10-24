package dev.kyralabs.pingogram.feature.settings.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.PowerSavingMode
import dev.kyralabs.pingogram.core.domain.model.PowerSavingPart
import dev.kyralabs.pingogram.core.testing.FakeBatterySaverRepository
import dev.kyralabs.pingogram.core.testing.FakePowerSavingRepository
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PowerSavingViewModelTest {
    private val repository = FakePowerSavingRepository()
    private val batterySaver = FakeBatterySaverRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** The view model, its state collected as by the screen. */
    private fun TestScope.viewModel(): PowerSavingViewModel {
        val viewModel = PowerSavingViewModel(repository, batterySaver)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun `shows nothing before the settings are read`() =
        runTest {
            assertNull(PowerSavingViewModel(repository, batterySaver).uiState.value.mode)
        }

    @Test
    fun `shows the saved settings, with Battery Saver's state`() =
        runTest {
            batterySaver.isOn.value = true

            val state = viewModel().uiState.value

            assertEquals(PowerSavingMode.WithBatterySaver, state.mode)
            assertEquals(PowerSavingPart.entries.toSet(), state.parts)
            assertTrue(state.isOn)
        }

    @Test
    fun `follows Battery Saver while the page shows`() =
        runTest {
            val viewModel = viewModel()
            assertFalse(viewModel.uiState.value.isOn)

            batterySaver.isOn.value = true
            runCurrent()

            assertTrue(viewModel.uiState.value.isBatterySaverOn)
        }

    @Test
    fun `saves the mode and the parts`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(PowerSavingEvent.SetMode(PowerSavingMode.Always))
            viewModel.onEvent(PowerSavingEvent.SetPart(PowerSavingPart.Autoplay, off = false))
            runCurrent()

            assertEquals(PowerSavingMode.Always, viewModel.uiState.value.mode)
            assertEquals(
                setOf(PowerSavingPart.AnimatedStickers, PowerSavingPart.CallAnimations),
                viewModel.uiState.value.parts,
            )
            assertTrue(viewModel.uiState.value.isOn)
        }

    @Test
    fun `a change that is not saved shows why and keeps what was saved`() =
        runTest {
            repository.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = viewModel()
            val effects = mutableListOf<PowerSavingEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            viewModel.onEvent(PowerSavingEvent.SetMode(PowerSavingMode.Off))
            runCurrent()

            assertEquals(PowerSavingMode.WithBatterySaver, viewModel.uiState.value.mode)
            assertEquals(listOf(PowerSavingEffect.ShowError(DomainError.Unknown(0, "Disk full"))), effects)
        }
}
