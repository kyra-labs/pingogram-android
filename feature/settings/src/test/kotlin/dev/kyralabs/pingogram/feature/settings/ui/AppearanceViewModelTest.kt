package dev.kyralabs.pingogram.feature.settings.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.SendKeys
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import dev.kyralabs.pingogram.core.testing.FakeAppearanceRepository
import dev.kyralabs.pingogram.core.testing.FakeComposerSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakePlaybackSettingsRepository
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

    private val composerSettings = FakeComposerSettingsRepository()

    private val playbackSettings = FakePlaybackSettingsRepository()

    private fun TestScope.viewModel() =
        AppearanceViewModel(repository, composerSettings, playbackSettings).also { runCurrent() }

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

    @Test
    fun `Enter sends on a keyboard and not on the screen at first, and a change saves and shows`() =
        runTest {
            repository.save(AppearanceSettings())
            val viewModel = viewModel()
            assertEquals(SendKeys(), (viewModel.state as AppearanceUiState.Content).sendKeys)

            val changed = SendKeys(hardwareKeyboard = false, onScreenKeyboard = true)
            send(viewModel, AppearanceEvent.SetSendKeys(changed))

            assertEquals(changed, (viewModel.state as AppearanceUiState.Content).sendKeys)
        }

    @Test
    fun `a send key that cannot be saved stays as it was, with a snackbar`() =
        runTest {
            repository.save(AppearanceSettings())
            composerSettings.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, AppearanceEvent.SetSendKeys(SendKeys(onScreenKeyboard = true)))

            assertEquals(SendKeys(), (viewModel.state as AppearanceUiState.Content).sendKeys)
            assertTrue(effects.single() is AppearanceEffect.ShowError)
        }

    @Test
    fun `raise to listen is on at first, and each of its switches saves and shows`() =
        runTest {
            repository.save(AppearanceSettings())
            val viewModel = viewModel()
            assertEquals(RaiseToListenSettings(), (viewModel.state as AppearanceUiState.Content).raiseToListen)

            send(viewModel, AppearanceEvent.SetPauseWhenLowered(true))
            send(viewModel, AppearanceEvent.SetRaiseToListen(false))

            assertEquals(
                RaiseToListenSettings(enabled = false, pauseWhenLowered = true),
                (viewModel.state as AppearanceUiState.Content).raiseToListen,
            )
        }

    @Test
    fun `raise to listen that cannot be saved stays as it was, with a snackbar`() =
        runTest {
            repository.save(AppearanceSettings())
            playbackSettings.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, AppearanceEvent.SetRaiseToListen(false))

            assertTrue((viewModel.state as AppearanceUiState.Content).raiseToListen.enabled)
            assertTrue(effects.single() is AppearanceEffect.ShowError)
        }
}
