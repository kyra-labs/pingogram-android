package dev.kyralabs.pingogram.feature.settings.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.testing.FakeAppearanceRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {
    private val repository = FakeAppearanceRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel() = AppearanceViewModel(repository).also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: AppearanceViewModel): List<AppearanceEffect> {
        val effects = mutableListOf<AppearanceEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: AppearanceViewModel,
        event: AppearanceEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    private val AppearanceViewModel.state get() = uiState.value

    @Test
    fun `is loading until the settings are read`() =
        runTest {
            assertEquals(AppearanceUiState.Loading, viewModel().state)
        }

    @Test
    fun `shows the saved settings`() =
        runTest {
            val viewModel = viewModel()

            repository.save(AppearanceSettings(themeMode = ThemeMode.Dark, amoled = true))
            runCurrent()

            assertEquals(
                AppearanceUiState.Content(AppearanceSettings(themeMode = ThemeMode.Dark, amoled = true)),
                viewModel.state,
            )
        }

    @Test
    fun `choosing a theme saves it and shows it`() =
        runTest {
            repository.save(AppearanceSettings())
            val viewModel = viewModel()

            send(viewModel, AppearanceEvent.SetThemeMode(ThemeMode.Light))

            assertEquals(AppearanceUiState.Content(AppearanceSettings(themeMode = ThemeMode.Light)), viewModel.state)
        }

    @Test
    fun `turning dynamic color off saves it`() =
        runTest {
            repository.save(AppearanceSettings())
            val viewModel = viewModel()

            send(viewModel, AppearanceEvent.SetDynamicColor(false))

            assertEquals(AppearanceUiState.Content(AppearanceSettings(dynamicColor = false)), viewModel.state)
        }

    @Test
    fun `turning AMOLED black on saves it`() =
        runTest {
            repository.save(AppearanceSettings())
            val viewModel = viewModel()

            send(viewModel, AppearanceEvent.SetAmoled(true))

            assertEquals(AppearanceUiState.Content(AppearanceSettings(amoled = true)), viewModel.state)
        }

    @Test
    fun `choosing a contrast level saves it`() =
        runTest {
            repository.save(AppearanceSettings())
            val viewModel = viewModel()

            send(viewModel, AppearanceEvent.SetContrast(Contrast.High))

            assertEquals(AppearanceUiState.Content(AppearanceSettings(contrast = Contrast.High)), viewModel.state)
        }

    @Test
    fun `a change that cannot be saved shows the error and changes nothing`() =
        runTest {
            repository.save(AppearanceSettings())
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            val error = DomainError.Unknown(0, "Disk full")
            repository.failure = error

            send(viewModel, AppearanceEvent.SetThemeMode(ThemeMode.Dark))

            assertEquals(listOf(AppearanceEffect.ShowError(error)), effects)
            assertEquals(AppearanceUiState.Content(AppearanceSettings()), viewModel.state)
        }

    @Test
    fun `saved changes show no message`() =
        runTest {
            repository.save(AppearanceSettings())
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, AppearanceEvent.SetAmoled(true))

            assertTrue(effects.isEmpty())
        }
}
