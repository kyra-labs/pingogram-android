package dev.kyralabs.pingogram.feature.settings.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
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
class ColorThemeViewModelTest {
    private val repository = FakeAppearanceRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel() = ColorThemeViewModel(repository).also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: ColorThemeViewModel): List<ColorThemeEffect> {
        val effects = mutableListOf<ColorThemeEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.select(
        viewModel: ColorThemeViewModel,
        option: ColorThemeOption,
    ) {
        viewModel.select(option)
        runCurrent()
    }

    private val ColorThemeViewModel.settings
        get() = (uiState.value as ColorThemeUiState.Content).settings

    @Test
    fun `is loading until the settings are read`() =
        runTest {
            assertEquals(ColorThemeUiState.Loading, viewModel().uiState.value)
        }

    @Test
    fun `picking a theme saves it and turns the wallpaper's colors off`() =
        runTest {
            repository.save(AppearanceSettings(themeMode = ThemeMode.Dark))
            val viewModel = viewModel()

            select(viewModel, ColorThemeOption.Palette(ThemePalette.Teal))

            assertEquals(
                AppearanceSettings(themeMode = ThemeMode.Dark, dynamicColor = false, palette = ThemePalette.Teal),
                viewModel.settings,
            )
        }

    @Test
    fun `picking the wallpaper's colors turns them on and keeps the last theme for where they have none`() =
        runTest {
            repository.save(AppearanceSettings(dynamicColor = false, palette = ThemePalette.Pink))
            val viewModel = viewModel()

            select(viewModel, ColorThemeOption.Wallpaper)

            assertEquals(AppearanceSettings(dynamicColor = true, palette = ThemePalette.Pink), viewModel.settings)
        }

    @Test
    fun `the theme in use is the wallpaper's only where the device has wallpaper colors`() {
        val settings = AppearanceSettings(dynamicColor = true, palette = ThemePalette.Lime)

        assertEquals(ColorThemeOption.Wallpaper, settings.colorTheme(wallpaperAvailable = true))
        assertEquals(ColorThemeOption.Palette(ThemePalette.Lime), settings.colorTheme(wallpaperAvailable = false))
        assertEquals(
            ColorThemeOption.Palette(ThemePalette.Lime),
            settings.copy(dynamicColor = false).colorTheme(wallpaperAvailable = true),
        )
    }

    @Test
    fun `a theme that cannot be saved shows the error and changes nothing`() =
        runTest {
            repository.save(AppearanceSettings())
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            val error = DomainError.Unknown(0, "Disk full")
            repository.failure = error

            select(viewModel, ColorThemeOption.Palette(ThemePalette.Red))

            assertEquals(listOf(ColorThemeEffect.ShowError(error)), effects)
            assertEquals(AppearanceSettings(), viewModel.settings)
        }

    @Test
    fun `a saved theme shows no message`() =
        runTest {
            repository.save(AppearanceSettings())
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            select(viewModel, ColorThemeOption.Palette(ThemePalette.Monochrome))

            assertTrue(effects.isEmpty())
        }
}
