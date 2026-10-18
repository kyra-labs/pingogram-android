package dev.kyralabs.pingogram.core.ui.theme

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalRippleThemeConfiguration
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.RippleDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode

/**
 * Root theme of every Pingogram screen (`docs/DESIGN_SYSTEM.md` §1): Material 3 Expressive with the
 * expressive motion scheme, Google Sans Flex, the expressive shape scale, [PingogramColors],
 * [PingogramSpacing] and [PingogramSizes].
 *
 * The typography follows the system's "Bold text" setting (Android 12+) without doubling it; see
 * [pingogramTypography].
 *
 * Ripples are the Material 3 opacity state layers (hover 8%, focus 10%, pressed 10%, dragged 16%).
 * Reduced motion needs no extra code: Compose scales every animation, springs included, by the
 * system animator duration scale, and a scale of 0 makes them finish immediately.
 *
 * @param dynamicColor Material You colors on Android 12+; brand colors otherwise or when false.
 * @param contrast used where the system has no contrast setting (before Android 14).
 * @param amoled true-black surfaces in dark theme.
 */
@Composable
fun PingogramTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = true,
    contrast: Contrast = Contrast.Standard,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = themeMode.isDark()
    val colorScheme = rememberPingogramColorScheme(dark, dynamicColor, contrast, amoled)
    val pingogramColors = remember(colorScheme, dark) { pingogramColors(colorScheme, dark) }
    val settings = remember(dynamicColor, contrast, amoled) { ThemeSettings(dynamicColor, contrast, amoled) }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = PingogramShapes,
        typography = pingogramTypography(dark, LocalConfiguration.current.fontWeightAdjustmentOrZero()),
    ) {
        CompositionLocalProvider(
            LocalPingogramColors provides pingogramColors,
            LocalPingogramSpacing provides DefaultSpacing,
            LocalPingogramSizes provides DefaultSizes,
            LocalRippleThemeConfiguration provides RippleDefaults.OpacityFocusThemeConfiguration,
            LocalThemeSettings provides settings,
            content = content,
        )
    }
}

/**
 * [content] in the dark theme of the enclosing [PingogramTheme], with its dynamic color, contrast and AMOLED settings,
 * whatever the theme mode: for immersive media, such as the media viewer, which shows photos and videos on black in
 * light theme too, as Google Photos does, so its chrome and components are legible on the `scrim` behind them.
 */
@Composable
fun PingogramDarkTheme(content: @Composable () -> Unit) {
    val settings = LocalThemeSettings.current
    PingogramTheme(
        themeMode = ThemeMode.Dark,
        dynamicColor = settings.dynamicColor,
        contrast = settings.contrast,
        amoled = settings.amoled,
        content = content,
    )
}

/** What [PingogramTheme] was given besides its theme mode, for [PingogramDarkTheme]. */
private data class ThemeSettings(
    val dynamicColor: Boolean,
    val contrast: Contrast,
    val amoled: Boolean,
)

private val LocalThemeSettings =
    staticCompositionLocalOf { ThemeSettings(dynamicColor = true, contrast = Contrast.Standard, amoled = false) }

/** Whether this mode resolves to the dark theme right now. */
@Composable
fun ThemeMode.isDark(): Boolean =
    when (this) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

/** The "Bold text" weight adjustment of Android 12+ (300 while it is on), or 0. */
private fun Configuration.fontWeightAdjustmentOrZero(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        fontWeightAdjustment != Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED
    ) {
        fontWeightAdjustment
    } else {
        0
    }

private val DefaultSpacing = PingogramSpacing()
private val DefaultSizes = PingogramSizes()
