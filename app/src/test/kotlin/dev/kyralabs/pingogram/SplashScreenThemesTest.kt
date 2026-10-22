package dev.kyralabs.pingogram

import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [splashScreenThemeOf] picks the splash screen whose background is the theme's (`docs/DESIGN_SYSTEM.md` §8). */
class SplashScreenThemesTest {
    @Test
    fun `the default settings keep the manifest's splash screen`() {
        assertEquals(MANIFEST_SPLASH_SCREEN_THEME, splashScreenThemeOf(AppearanceSettings()))
    }

    @Test
    fun `the contrast level keeps the splash screen`() {
        assertEquals(
            MANIFEST_SPLASH_SCREEN_THEME,
            splashScreenThemeOf(AppearanceSettings(contrast = Contrast.High)),
        )
    }

    @Test
    fun `brand colors in the system's theme mode`() {
        assertEquals(
            R.style.Theme_Pingogram_Starting_SystemBrand,
            splashScreenThemeOf(AppearanceSettings(dynamicColor = false)),
        )
    }

    @Test
    fun `AMOLED in the system's theme mode turns black only in the dark`() {
        assertEquals(
            R.style.Theme_Pingogram_Starting_SystemDynamicAmoled,
            splashScreenThemeOf(AppearanceSettings(amoled = true)),
        )
        assertEquals(
            R.style.Theme_Pingogram_Starting_SystemBrandAmoled,
            splashScreenThemeOf(AppearanceSettings(dynamicColor = false, amoled = true)),
        )
    }

    @Test
    fun `a chosen theme mode overrides the system's`() {
        assertEquals(
            R.style.Theme_Pingogram_Starting_Light_Dynamic,
            splashScreenThemeOf(AppearanceSettings(themeMode = ThemeMode.Light, amoled = true)),
        )
        assertEquals(
            R.style.Theme_Pingogram_Starting_Dark_Brand,
            splashScreenThemeOf(AppearanceSettings(themeMode = ThemeMode.Dark, dynamicColor = false)),
        )
    }

    @Test
    fun `AMOLED dark is black with dynamic and brand colors alike`() {
        val black = R.style.Theme_Pingogram_Starting_Dark_Black
        assertEquals(black, splashScreenThemeOf(AppearanceSettings(themeMode = ThemeMode.Dark, amoled = true)))
        assertEquals(
            black,
            splashScreenThemeOf(AppearanceSettings(themeMode = ThemeMode.Dark, dynamicColor = false, amoled = true)),
        )
    }

    @Test
    fun `a color theme has splash screens of its own background`() {
        val teal = AppearanceSettings(dynamicColor = false, palette = ThemePalette.Teal)

        assertEquals(R.style.Theme_Pingogram_Starting_SystemTeal, splashScreenThemeOf(teal))
        assertEquals(R.style.Theme_Pingogram_Starting_SystemTealAmoled, splashScreenThemeOf(teal.copy(amoled = true)))
        assertEquals(
            R.style.Theme_Pingogram_Starting_Light_Teal,
            splashScreenThemeOf(teal.copy(themeMode = ThemeMode.Light)),
        )
        assertEquals(
            R.style.Theme_Pingogram_Starting_Dark_Teal,
            splashScreenThemeOf(teal.copy(themeMode = ThemeMode.Dark)),
        )
    }

    @Test
    fun `AMOLED dark is black with a color theme too`() {
        assertEquals(
            R.style.Theme_Pingogram_Starting_Dark_Black,
            splashScreenThemeOf(
                AppearanceSettings(
                    themeMode = ThemeMode.Dark,
                    dynamicColor = false,
                    palette = ThemePalette.Pink,
                    amoled = true,
                ),
            ),
        )
    }

    @Test
    fun `the Pingogram theme keeps the brand's splash screens, and wallpaper colors ignore the saved theme`() {
        assertEquals(
            R.style.Theme_Pingogram_Starting_SystemBrand,
            splashScreenThemeOf(AppearanceSettings(dynamicColor = false, palette = ThemePalette.Pingogram)),
        )
        assertEquals(
            MANIFEST_SPLASH_SCREEN_THEME,
            splashScreenThemeOf(AppearanceSettings(dynamicColor = true, palette = ThemePalette.Lime)),
        )
    }

    @Test
    fun `every generated theme has its four splash screens`() {
        val themes =
            ThemePalette.entries.filter { it != ThemePalette.Pingogram }.flatMap { palette ->
                SplashMode.entries.map { paletteSplashTheme(palette, it) }
            }

        assertEquals(themes.size, themes.toSet().size)
    }
}
