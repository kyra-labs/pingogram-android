package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalRippleThemeConfiguration
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.RippleDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode

/**
 * Root theme of every Pingogram screen (`docs/DESIGN_SYSTEM.md` §1): Material 3 Expressive with the
 * expressive motion scheme, Google Sans Flex, the expressive shape scale, [PingogramColors],
 * [PingogramSpacing] and [PingogramSizes].
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
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = PingogramShapes,
        typography = pingogramTypography(dark),
    ) {
        CompositionLocalProvider(
            LocalPingogramColors provides pingogramColors,
            LocalPingogramSpacing provides DefaultSpacing,
            LocalPingogramSizes provides DefaultSizes,
            LocalRippleThemeConfiguration provides RippleDefaults.OpacityFocusThemeConfiguration,
            content = content,
        )
    }
}

/** Whether this mode resolves to the dark theme right now. */
@Composable
fun ThemeMode.isDark(): Boolean =
    when (this) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

private val DefaultSpacing = PingogramSpacing()
private val DefaultSizes = PingogramSizes()
