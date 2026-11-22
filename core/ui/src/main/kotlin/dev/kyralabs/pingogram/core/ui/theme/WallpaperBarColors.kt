package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * How a conversation's top bar shows over its blurred wallpaper (`docs/DESIGN_SYSTEM.md` §13.5, owner decision
 * 2026-11-22): it follows the theme, not the wallpaper's brightness. The scheme's `surface` is washed over the blur
 * at [washAlpha], so the bar's `onSurface` title and icons, and the status bar's icons, are the theme's and keep 4.5:1
 * over a top of any brightness: mid-dark over a bright photo and near-black over a dark one in dark theme.
 */
@Immutable
data class WallpaperBarColors(
    val washAlpha: Float,
) {
    /** The wash over the wallpaper: the enclosing scheme's `surface` at [washAlpha]. */
    @Composable
    fun wash(): Color = MaterialTheme.colorScheme.surface.copy(alpha = washAlpha)
}

/** The bar's colors in the theme's light or [dark]. */
fun wallpaperBarColors(dark: Boolean): WallpaperBarColors = if (dark) DarkTheme else LightTheme

// The least wash that keeps the bar's subtitle (`onSurfaceVariant`) and "typing…" (`primary`) at 4.5:1 over a black
// top in light theme (86%) and a white one in dark theme (73%), with some room (WallpaperBarColorsTest).
private val LightTheme = WallpaperBarColors(washAlpha = 0.88f)

private val DarkTheme = WallpaperBarColors(washAlpha = 0.75f)
