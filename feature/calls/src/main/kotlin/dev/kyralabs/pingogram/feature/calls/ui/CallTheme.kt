package dev.kyralabs.pingogram.feature.calls.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.isDark

/**
 * The theme of the call screens ([CallActivity], [GroupCallActivity]), activities of their own: the user's Appearance
 * settings, edge to edge with the system bars' icons for the theme.
 */
@Composable
internal fun ComponentActivity.CallTheme(
    settings: AppearanceSettings,
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
        content = content,
    )
}
