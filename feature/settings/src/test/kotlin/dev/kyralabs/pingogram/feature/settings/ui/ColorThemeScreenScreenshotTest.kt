package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import dev.kyralabs.pingogram.core.ui.theme.themeColorScheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Theme page (`docs/adr/0016-color-themes.md`): a generated theme chosen, in its own colors as the app shows it
 * then, light and dark; the wallpaper's colors chosen (a stand-in wallpaper); before Android 12, without them; on an
 * expanded window; and AMOLED black, high contrast and right to left (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ColorThemeScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingLight() =
        composeRule.captureScreen(javaClass, "loadingLight", ThemeMode.Light) {
            ColorThemeScreen(state = ColorThemeUiState.Loading, onSelect = {}, onBack = {})
        }

    @Test
    fun tealLight() = capture("tealLight", ThemeMode.Light, ThemePalette.Teal)

    @Test
    fun pinkDark() = capture("pinkDark", ThemeMode.Dark, ThemePalette.Pink)

    @Test
    fun monochromeLight() = capture("monochromeLight", ThemeMode.Light, ThemePalette.Monochrome)

    @Test
    fun withoutWallpaperColorsLight() =
        capture("withoutWallpaperColorsLight", ThemeMode.Light, ThemePalette.Pingogram, wallpaper = false)

    @Test
    fun wallpaperChosenDark() =
        composeRule.captureScreen(javaClass, "wallpaperChosenDark", ThemeMode.Dark) {
            ColorThemeScreen(
                state = ColorThemeUiState.Content(AppearanceSettings(themeMode = ThemeMode.Dark, dynamicColor = true)),
                onSelect = {},
                onBack = {},
                wallpaperSchemes = StandInWallpaper,
            )
        }

    @Test
    @Config(qualifiers = EXPANDED)
    fun tealExpanded() = capture("tealExpanded", ThemeMode.Light, ThemePalette.Teal)

    @Test
    fun amoled() = captureAudit(ScreenshotVariant.Amoled, ThemePalette.Green)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast, ThemePalette.Orange)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl, ThemePalette.Purple)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = captureAudit(ScreenshotVariant.Medium, ThemePalette.Indigo)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        palette: ThemePalette,
        wallpaper: Boolean = true,
    ) = composeRule.captureScreen(javaClass, name, themeMode, palette = palette) {
        ColorThemeScreen(
            state = ColorThemeUiState.Content(settings(themeMode, palette)),
            onSelect = {},
            onBack = {},
            wallpaperSchemes = StandInWallpaper.takeIf { wallpaper },
        )
    }

    private fun captureAudit(
        variant: ScreenshotVariant,
        palette: ThemePalette,
    ) = composeRule.captureScreen(javaClass, variant.goldenName, variant, palette) {
        ColorThemeScreen(
            state = ColorThemeUiState.Content(settings(variant.themeMode, palette).copy(amoled = variant.amoled)),
            onSelect = {},
            onBack = {},
            wallpaperSchemes = StandInWallpaper,
        )
    }

    private fun settings(
        themeMode: ThemeMode,
        palette: ThemePalette,
    ) = AppearanceSettings(themeMode = themeMode, dynamicColor = false, palette = palette)

    private companion object {
        /** A wallpaper's colors, the same in every run: those of the Blue theme. */
        val StandInWallpaper =
            WallpaperSchemes(
                light = themeColorScheme(ThemePalette.Blue, dark = false, Contrast.Standard),
                dark = themeColorScheme(ThemePalette.Blue, dark = true, Contrast.Standard),
            )
    }
}
