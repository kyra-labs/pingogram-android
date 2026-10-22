package dev.kyralabs.pingogram.core.domain.model

/**
 * The Appearance settings (`REQUIREMENTS.md` FR-S2) that `PingogramTheme` follows (`docs/DESIGN_SYSTEM.md` §1, §2.1).
 * The defaults are what the app looks like before the user changes anything.
 *
 * @property themeMode light, dark, or the system's choice.
 * @property dynamicColor Material You colors from the wallpaper (Android 12+); the colors of [palette] otherwise.
 * @property palette the color theme used without the wallpaper's colors: when [dynamicColor] is off, before Android
 *   12, and for a medium or high [contrast] where the wallpaper's colors have no contrast levels.
 * @property contrast the contrast level where the system has no contrast setting (before Android 14).
 * @property amoled true-black surfaces in dark theme.
 */
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val palette: ThemePalette = ThemePalette.Pingogram,
    val contrast: Contrast = Contrast.Standard,
    val amoled: Boolean = false,
)
