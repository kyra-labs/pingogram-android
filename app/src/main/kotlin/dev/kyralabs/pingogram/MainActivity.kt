package dev.kyralabs.pingogram

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.ui.PingogramApp

/**
 * The only activity. Draws edge to edge behind transparent system bars; Compose handles the insets
 * (`REQUIREMENTS.md` §7).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate: switches from Theme.Pingogram.Starting to the post-splash theme.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PingogramTheme {
                PingogramApp()
            }
        }
    }
}
