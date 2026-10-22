package dev.kyralabs.pingogram.core.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemePalette

/**
 * The [ColorScheme] for the current settings (`docs/DESIGN_SYSTEM.md` §2.1): Material You on
 * Android 12+ when [dynamicColor] is on ([systemDynamicColors]), otherwise the color theme [palette]'s scheme
 * (generated into `Color.kt`).
 *
 * [contrast] is the level in effect: [PingogramTheme] passes the system's on Android 14+ ([systemContrastOrNull]),
 * which the platform applies to dynamic schemes read from its color roles too.
 */
@Composable
fun rememberPingogramColorScheme(
    dark: Boolean,
    dynamicColor: Boolean,
    palette: ThemePalette,
    contrast: Contrast,
    amoled: Boolean,
): ColorScheme {
    val context = LocalContext.current
    // Wallpaper and system theme changes arrive as configuration changes; they refresh dynamic colors.
    val configuration = LocalConfiguration.current
    return remember(context, configuration, dark, dynamicColor, palette, contrast, amoled) {
        val dynamic =
            if (dynamicColor &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                systemDynamicColors(context, dark)
            } else {
                null
            }
        pingogramColorScheme(
            dark = dark,
            palette = palette,
            contrast = contrast,
            amoled = amoled,
            dynamicScheme = dynamic?.scheme,
            dynamicSchemeFollowsContrast = dynamic?.followsContrast ?: false,
        )
    }
}

/**
 * The light scheme for what Pingogram draws outside its own windows, where the system decides light or dark, e.g. the
 * placeholder avatars of notifications: Material You from the wallpaper on Android 12+ when [dynamicColor] is on, the
 * color theme [palette]'s otherwise. Not composable, so code without a composition (a notification renderer) can use
 * it.
 */
fun standaloneColorScheme(
    context: Context,
    dynamicColor: Boolean,
    palette: ThemePalette,
): ColorScheme =
    if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        systemDynamicColors(context, dark = false).scheme
    } else {
        themeColorScheme(palette, dark = false, contrast = Contrast.Standard)
    }

/**
 * The wallpaper's Material You scheme for [dark] at standard contrast (Android 12+, [systemDynamicColors]), or null
 * before Android 12, which has none: e.g. for the theme picker's "Wallpaper colors" chip.
 */
fun wallpaperColorScheme(
    context: Context,
    dark: Boolean,
): ColorScheme? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) systemDynamicColors(context, dark).scheme else null

/**
 * Picks the scheme for the given settings. [dynamicScheme] is the platform's Material You scheme, or
 * null when dynamic color is off or unavailable. Dynamic schemes from the tonal palettes (before Android 14, or where
 * the system leaves its color roles stale) have no contrast levels ([dynamicSchemeFollowsContrast] = false), so a
 * medium or high [contrast] falls back to the color theme [palette] there: accessibility wins over wallpaper colors.
 */
internal fun pingogramColorScheme(
    dark: Boolean,
    palette: ThemePalette,
    contrast: Contrast,
    amoled: Boolean,
    dynamicScheme: ColorScheme?,
    dynamicSchemeFollowsContrast: Boolean,
): ColorScheme {
    val useDynamic = dynamicScheme != null && (dynamicSchemeFollowsContrast || contrast == Contrast.Standard)
    val scheme = if (useDynamic) dynamicScheme else themeColorScheme(palette, dark, contrast)
    return if (dark && amoled) scheme.withAmoledSurfaces() else scheme
}

/**
 * The color theme [palette]'s scheme for [dark] and [contrast] (`docs/DESIGN_SYSTEM.md` §2.1), built from the
 * generated table the first time it is asked for and kept: the same scheme object every time, so what is keyed on it
 * (the cross-fade, [pingogramColors]) does not start over. A race builds an equal scheme twice, harmlessly.
 */
fun themeColorScheme(
    palette: ThemePalette,
    dark: Boolean,
    contrast: Contrast,
): ColorScheme {
    val index = (palette.ordinal * 2 + if (dark) 1 else 0) * Contrast.entries.size + contrast.ordinal
    return ThemeSchemes[index] ?: paletteColorScheme(palette, dark, contrast).also { ThemeSchemes[index] = it }
}

/** The schemes [themeColorScheme] built so far: every theme × light and dark × every contrast level. */
private val ThemeSchemes = arrayOfNulls<ColorScheme>(ThemePalette.entries.size * 2 * Contrast.entries.size)

/**
 * AMOLED dark (§2.1): true-black background, surface, surfaceDim and surfaceContainerLowest, and
 * every other container one step down the tonal ladder. Content (`on*`) roles stay unchanged.
 * surfaceDim follows surface because the two are the same tone in every M3 dark scheme.
 */
internal fun ColorScheme.withAmoledSurfaces(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = surfaceContainerLowest,
        surfaceContainer = surfaceContainerLow,
        surfaceContainerHigh = surfaceContainer,
        surfaceContainerHighest = surfaceContainerHigh,
    )

/**
 * The system's contrast level, which the theme follows instead of the app's setting on Android 14+
 * ([PingogramTheme]: its colors and its typography), updated when it changes; null before Android 14, which has none.
 */
@Composable
fun systemContrastOrNull(): Contrast? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) systemContrast() else null

/** Maps `UiModeManager.getContrast()` (0 standard, 0.5 medium, 1 high) like Material Components does. */
internal fun contrastFromSystemLevel(level: Float): Contrast =
    when {
        level >= 2f / 3f -> Contrast.High
        level >= 1f / 3f -> Contrast.Medium
        else -> Contrast.Standard
    }

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
private fun systemContrast(): Contrast {
    val context = LocalContext.current
    val uiModeManager = remember(context) { context.getSystemService(UiModeManager::class.java) }
    var level by remember(uiModeManager) { mutableFloatStateOf(uiModeManager.contrast) }
    DisposableEffect(uiModeManager) {
        val listener = UiModeManager.ContrastChangeListener { level = it }
        uiModeManager.addContrastChangeListener(context.mainExecutor, listener)
        onDispose { uiModeManager.removeContrastChangeListener(listener) }
    }
    return contrastFromSystemLevel(level)
}
