package dev.kyralabs.pingogram

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.isDark
import dev.kyralabs.pingogram.ui.PingogramApp

/**
 * The only activity. Draws edge to edge behind transparent system bars; Compose handles the insets
 * (`REQUIREMENTS.md` §7). The splash screen stays until TDLib reports whether the account is logged in and the
 * Appearance settings are read, for at most 300 ms (`docs/PERFORMANCE.md` §2).
 *
 * The theme follows the Appearance settings ([MainActivityViewModel.appearance]), and so do the system bars' icons:
 * light on a dark theme, dark on a light one, whatever the system's own setting. Nothing is composed until the settings
 * are read, so the app never draws in a theme the user did not choose; the splash screen covers that wait.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate: switches from Theme.Pingogram.Starting to the post-splash theme.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // The bound reference creates the view model now, which starts listening to TDLib, reading the settings and
        // the splash time limit.
        splashScreen.setKeepOnScreenCondition(viewModel::keepSplashScreen)
        enableEdgeToEdge()
        setContent {
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            appearance?.let { settings ->
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                AppTheme(settings) { PingogramApp(uiState = uiState) }
            }
        }
    }

    /** [PingogramTheme] for [settings], with system bar icons that contrast with the theme it resolves to. */
    @Composable
    private fun AppTheme(
        settings: AppearanceSettings,
        content: @Composable () -> Unit,
    ) {
        val dark = settings.themeMode.isDark()
        LaunchedEffect(dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
            )
        }
        PingogramTheme(
            themeMode = settings.themeMode,
            dynamicColor = settings.dynamicColor,
            contrast = settings.contrast,
            amoled = settings.amoled,
            content = content,
        )
    }

    private companion object {
        // enableEdgeToEdge()'s own scrims behind three-button navigation where the system draws none (before Android
        // 10), which androidx.activity does not expose: the platform's light and dark navigation bar scrims.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
