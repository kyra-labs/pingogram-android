package dev.kyralabs.pingogram

import androidx.annotation.StyleRes
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.ThemePalette

/** The manifest's own splash screen theme (`Resources.ID_NULL`), `Theme.Pingogram.Starting`. */
internal const val MANIFEST_SPLASH_SCREEN_THEME = 0

/**
 * The splash screen theme whose background is that of the theme [settings] resolve to (`docs/DESIGN_SYSTEM.md` §8):
 * the theme mode, dynamic color or the color theme, and true black for AMOLED dark. The contrast level changes no
 * background. [MANIFEST_SPLASH_SCREEN_THEME] for the default settings, whose theme the manifest's covers. The themes of
 * the color themes other than Pingogram are generated ([paletteSplashTheme], `tools/theme`).
 *
 * Android 13+ keeps the theme an app sets ([MainActivity]) for the splash screens of its next starts; before, every
 * splash screen is the manifest's, in the system's theme mode.
 */
@StyleRes
internal fun splashScreenThemeOf(settings: AppearanceSettings): Int {
    val palette = settings.palette.takeUnless { settings.dynamicColor || it == ThemePalette.Pingogram }
    return when (settings.themeMode) {
        ThemeMode.System -> systemSplashTheme(settings, palette)
        ThemeMode.Light -> lightSplashTheme(settings, palette)
        ThemeMode.Dark -> darkSplashTheme(settings, palette)
    }
}

/** In the system's theme mode: [palette]'s (a color theme's other than Pingogram), the wallpaper's or the brand's. */
@StyleRes
private fun systemSplashTheme(
    settings: AppearanceSettings,
    palette: ThemePalette?,
): Int =
    when {
        palette != null -> {
            paletteSplashTheme(
                palette,
                if (settings.amoled) SplashMode.SystemAmoled else SplashMode.System,
            )
        }

        settings.dynamicColor && settings.amoled -> {
            R.style.Theme_Pingogram_Starting_SystemDynamicAmoled
        }

        settings.dynamicColor -> {
            MANIFEST_SPLASH_SCREEN_THEME
        }

        settings.amoled -> {
            R.style.Theme_Pingogram_Starting_SystemBrandAmoled
        }

        else -> {
            R.style.Theme_Pingogram_Starting_SystemBrand
        }
    }

@StyleRes
private fun lightSplashTheme(
    settings: AppearanceSettings,
    palette: ThemePalette?,
): Int =
    when {
        palette != null -> paletteSplashTheme(palette, SplashMode.Light)
        settings.dynamicColor -> R.style.Theme_Pingogram_Starting_Light_Dynamic
        else -> R.style.Theme_Pingogram_Starting_Light_Brand
    }

@StyleRes
private fun darkSplashTheme(
    settings: AppearanceSettings,
    palette: ThemePalette?,
): Int =
    when {
        settings.amoled -> R.style.Theme_Pingogram_Starting_Dark_Black
        palette != null -> paletteSplashTheme(palette, SplashMode.Dark)
        settings.dynamicColor -> R.style.Theme_Pingogram_Starting_Dark_Dynamic
        else -> R.style.Theme_Pingogram_Starting_Dark_Brand
    }
