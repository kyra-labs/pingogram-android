package dev.kyralabs.pingogram.core.domain.model

/**
 * The Appearance settings (`REQUIREMENTS.md` FR-S2) that `PingogramTheme` follows (`docs/DESIGN_SYSTEM.md` §1, §2.1).
 * The defaults are what the app looks like before the user changes anything.
 *
 * @property themeMode light, dark, or the system's choice.
 * @property dynamicColor Material You colors from the wallpaper (Android 12+); the brand colors otherwise.
 * @property contrast the contrast level where the system has no contrast setting (before Android 14).
 * @property amoled true-black surfaces in dark theme.
 */
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val contrast: Contrast = Contrast.Standard,
    val amoled: Boolean = false,
)
