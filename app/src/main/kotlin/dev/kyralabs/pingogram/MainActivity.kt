package dev.kyralabs.pingogram

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.ui.PingogramApp

/**
 * The only activity. Draws edge to edge behind transparent system bars; Compose handles the insets
 * (`REQUIREMENTS.md` §7). The splash screen stays until TDLib reports whether the account is logged in, for at most
 * 300 ms (`docs/PERFORMANCE.md` §2).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate: switches from Theme.Pingogram.Starting to the post-splash theme.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // The bound reference creates the view model now, which starts listening to TDLib and the splash time limit.
        splashScreen.setKeepOnScreenCondition(viewModel::keepSplashScreen)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            PingogramTheme {
                PingogramApp(uiState = uiState)
            }
        }
    }
}
