package dev.kyralabs.pingogram

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.repository.LockState
import dev.kyralabs.pingogram.core.notifications.NotificationIntents
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.isDark
import dev.kyralabs.pingogram.feature.settings.ui.passcode.AppLockRoute
import dev.kyralabs.pingogram.ui.LockableApp
import dev.kyralabs.pingogram.ui.PingogramApp

/**
 * The only activity. Draws edge to edge behind transparent system bars; Compose handles the insets
 * (`REQUIREMENTS.md` §7). The splash screen stays until TDLib reports whether the account is logged in and the
 * Appearance settings are read, for at most 300 ms (`docs/PERFORMANCE.md` §2).
 *
 * The theme follows the Appearance settings ([MainActivityViewModel.appearance]), and so do the system bars' icons:
 * light on a dark theme, dark on a light one, whatever the system's own setting. Nothing is composed until the settings
 * are read, so the app never draws in a theme the user did not choose; the splash screen covers that wait.
 *
 * A notification or a conversation shortcut opens a chat through the intent that starts the activity, or, when it runs
 * already, through [onNewIntent] ([NotificationIntents.chatIdOf]). A restored activity (rotation, process death) does
 * not act on its old intent again: its restored back stack shows the chat already.
 *
 * The passcode lock (`REQUIREMENTS.md` FR-S1, [MainActivityViewModel.lock]) shows its lock screen instead of the app
 * ([LockableApp]); a chat a notification asked for opens only once it is unlocked. The activity tells the lock when the
 * app leaves the screen and comes back ([onStop], [onStart]), but not for a configuration change. While locked, the
 * window is secure (no screenshots, no screen recording of the passcode entered); "Hide content in recent apps" keeps
 * the content out of the recent apps: through [setRecentsScreenshotEnabled] on Android 13 and later, which still
 * allows screenshots, and with [WindowManager.LayoutParams.FLAG_SECURE] before, which does not.
 *
 * Test tags show as resource ids, so the Macrobenchmarks and the Baseline Profile generator (`:benchmark`) find the
 * lists they scroll with UiAutomator (`docs/PERFORMANCE.md` §9).
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
        if (savedInstanceState == null) openChatOf(intent)
        setContent {
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            val lock by viewModel.lock.collectAsStateWithLifecycle()
            val settings = appearance
            if (settings != null && lock != LockState.Unknown) {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val chatToOpen by viewModel.chatToOpen.collectAsStateWithLifecycle()
                val hideContent by viewModel.hideContent.collectAsStateWithLifecycle()
                val locked = lock == LockState.Locked
                SecureWindowEffect(locked = locked, hideContent = hideContent)
                AppTheme(settings) {
                    LockableApp(
                        locked = locked,
                        lockScreen = { AppLockRoute() },
                        modifier = Modifier.semantics { testTagsAsResourceId = true },
                    ) {
                        PingogramApp(
                            uiState = uiState,
                            chatToOpen = chatToOpen,
                            onChatOpened = viewModel::onChatOpened,
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppShown()
    }

    override fun onStop() {
        super.onStop()
        // A rotation stops and starts the activity again at once; the app never left the screen.
        if (!isChangingConfigurations) viewModel.onAppHidden()
    }

    /**
     * Keeps the window secure while [locked], or, before Android 13, while [hideContent] (the only way to keep the
     * content out of the recent apps there), and from Android 13 on keeps the content out of the recent apps with
     * [setRecentsScreenshotEnabled] while [hideContent].
     */
    @Composable
    private fun SecureWindowEffect(
        locked: Boolean,
        hideContent: Boolean,
    ) {
        val recentsScreenshots = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val secure = locked || (hideContent && !recentsScreenshots)
        SideEffect {
            if (secure) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            if (recentsScreenshots) setRecentsScreenshotEnabled(!hideContent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openChatOf(intent)
    }

    /** Opens the chat [intent] names, if it is a notification's or a conversation shortcut's. */
    private fun openChatOf(intent: Intent?) {
        NotificationIntents.chatIdOf(intent)?.let(viewModel::openChat)
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
