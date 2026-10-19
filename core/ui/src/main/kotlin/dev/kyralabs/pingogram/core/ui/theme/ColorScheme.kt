package dev.kyralabs.pingogram.core.ui.theme

import android.app.UiModeManager
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

/**
 * The [ColorScheme] for the current settings (`docs/DESIGN_SYSTEM.md` §2.1): Material You on
 * Android 12+ when [dynamicColor] is on ([systemDynamicColors]), otherwise the brand schemes from `Color.kt`.
 *
 * On Android 14+ the system contrast setting replaces [contrast], and the platform applies it to
 * dynamic schemes read from its color roles too.
 */
@Composable
fun rememberPingogramColorScheme(
    dark: Boolean,
    dynamicColor: Boolean,
    contrast: Contrast,
    amoled: Boolean,
): ColorScheme {
    val context = LocalContext.current
    // Wallpaper and system theme changes arrive as configuration changes; they refresh dynamic colors.
    val configuration = LocalConfiguration.current
    val effectiveContrast =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) systemContrast() else contrast
    return remember(context, configuration, dark, dynamicColor, effectiveContrast, amoled) {
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
            contrast = effectiveContrast,
            amoled = amoled,
            dynamicScheme = dynamic?.scheme,
            dynamicSchemeFollowsContrast = dynamic?.followsContrast ?: false,
        )
    }
}

/**
 * Picks the scheme for the given settings. [dynamicScheme] is the platform's Material You scheme, or
 * null when dynamic color is off or unavailable. Dynamic schemes from the tonal palettes (before Android 14, or where
 * the system leaves its color roles stale) have no contrast levels ([dynamicSchemeFollowsContrast] = false), so a
 * medium or high [contrast] falls back to the brand scheme there: accessibility wins over wallpaper colors.
 */
internal fun pingogramColorScheme(
    dark: Boolean,
    contrast: Contrast,
    amoled: Boolean,
    dynamicScheme: ColorScheme?,
    dynamicSchemeFollowsContrast: Boolean,
): ColorScheme {
    val useDynamic = dynamicScheme != null && (dynamicSchemeFollowsContrast || contrast == Contrast.Standard)
    val scheme = if (useDynamic) dynamicScheme else brandColorScheme(dark, contrast)
    return if (dark && amoled) scheme.withAmoledSurfaces() else scheme
}

internal fun brandColorScheme(
    dark: Boolean,
    contrast: Contrast,
): ColorScheme =
    when (contrast) {
        Contrast.Standard -> if (dark) BrandDarkColorScheme else BrandLightColorScheme
        Contrast.Medium -> if (dark) BrandDarkMediumContrastColorScheme else BrandLightMediumContrastColorScheme
        Contrast.High -> if (dark) BrandDarkHighContrastColorScheme else BrandLightHighContrastColorScheme
    }

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
 * ([rememberPingogramColorScheme]), updated when it changes; null before Android 14, which has none.
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
