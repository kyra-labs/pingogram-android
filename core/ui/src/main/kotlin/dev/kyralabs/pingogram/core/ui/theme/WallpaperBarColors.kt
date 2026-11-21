package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.kyralabs.pingogram.core.domain.model.ThemeMode

/**
 * How a conversation's top bar shows over its blurred wallpaper (`docs/DESIGN_SYSTEM.md` §13.5, owner decision
 * 2026-11-21): in the dark scheme over a dark top ([darkTop]) and the light one over a light top, whatever the app's
 * theme, with that scheme's `surfaceContainer` washed over the wallpaper at [washAlpha], so its text keeps 4.5:1. The
 * status bar's icons follow: dark over a light top.
 */
@Immutable
data class WallpaperBarColors(
    val darkTop: Boolean,
    val washAlpha: Float,
) {
    val darkStatusBarIcons: Boolean get() = !darkTop

    /** The wash over the wallpaper, in the bar's own scheme ([WallpaperBarTheme]). */
    @Composable
    fun wash(): Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = washAlpha)
}

/** The bar's colors for a wallpaper whose top has the mean relative luminance [topLuminance] (0 black, 1 white). */
fun wallpaperBarColors(topLuminance: Float): WallpaperBarColors = if (topLuminance >= LIGHT_TOP) LightTop else DarkTop

/**
 * [content] in the scheme of [bar]'s polarity: the app's theme in light or dark, in the wallpaper's colors ([seed],
 * [WallpaperColorsTheme]) where it has them. Without [bar] (no wallpaper, or its top not measured yet), the enclosing
 * theme as it is.
 */
@Composable
fun WallpaperBarTheme(
    bar: WallpaperBarColors?,
    seed: Int?,
    content: @Composable () -> Unit,
) {
    val settings = LocalThemeSettings.current
    val dark = bar?.darkTop ?: settings.dark
    PingogramTheme(
        themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light,
        dynamicColor = settings.dynamicColor,
        palette = settings.palette,
        contrast = settings.contrast,
        amoled = settings.amoled,
        chat = settings.chat,
    ) { WallpaperColorsTheme(seed, content = content) }
}

/** Where a wallpaper's top counts as light: mid-grey (L* 50) has a luminance of 0.18. */
private const val LIGHT_TOP = 0.3f

private val LightTop = WallpaperBarColors(darkTop = false, washAlpha = 0.8f)

private val DarkTop = WallpaperBarColors(darkTop = true, washAlpha = 0.6f)
