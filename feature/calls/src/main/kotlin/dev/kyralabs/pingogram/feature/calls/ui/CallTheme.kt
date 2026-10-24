package dev.kyralabs.pingogram.feature.calls.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.isDark

/**
 * The theme of the call screens ([CallActivity], [GroupCallActivity]), activities of their own: the user's Appearance
 * settings, edge to edge with the system bars' icons for the theme. While power saving turns the call screens'
 * animations off ([stillAnimations], `PowerSaving.stillCalls`), [LocalStillCallAnimations] tells the screens.
 */
@Composable
internal fun ComponentActivity.CallTheme(
    settings: AppearanceSettings,
    stillAnimations: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = settings.themeMode.isDark()
    LaunchedEffect(dark) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
        )
    }
    PingogramTheme(
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColor,
        palette = settings.palette,
        contrast = settings.contrast,
        amoled = settings.amoled,
    ) {
        CompositionLocalProvider(LocalStillCallAnimations provides stillAnimations, content = content)
    }
}

/**
 * Whether power saving holds the call screens' animations still: the ringing avatar's turn and the speaking rings'
 * fades. Reduced motion stays the system's (`isReducedMotion`); this adds to it.
 */
internal val LocalStillCallAnimations = compositionLocalOf { false }
