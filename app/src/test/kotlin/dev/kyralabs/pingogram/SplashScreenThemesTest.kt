package dev.kyralabs.pingogram

import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
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
}
