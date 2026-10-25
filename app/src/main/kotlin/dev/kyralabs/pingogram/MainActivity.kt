package dev.kyralabs.pingogram

import android.content.Context
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
import androidx.annotation.RequiresApi
import androidx.annotation.StyleRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.LockState
import dev.kyralabs.pingogram.core.domain.repository.PhoneNumberVisibilityRepository
import dev.kyralabs.pingogram.core.notifications.NotificationIntents
import dev.kyralabs.pingogram.core.ui.component.LocalCallStarter
import dev.kyralabs.pingogram.core.ui.component.LocalLockedChatGate
import dev.kyralabs.pingogram.core.ui.component.LocalSecureWindow
import dev.kyralabs.pingogram.core.ui.component.LocalVoiceChats
import dev.kyralabs.pingogram.core.ui.component.SecureWindowRequests
import dev.kyralabs.pingogram.core.ui.sticker.AnimatedStickers
import dev.kyralabs.pingogram.core.ui.sticker.LocalAnimatedStickers
import dev.kyralabs.pingogram.core.ui.text.LocalPhoneNumbersHidden
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.isDark
import dev.kyralabs.pingogram.feature.calls.Calls
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareInbox
import dev.kyralabs.pingogram.feature.settings.ui.lockedchats.LockedChatGate
import dev.kyralabs.pingogram.feature.settings.ui.passcode.AppLockRoute
import dev.kyralabs.pingogram.language.PlatformAppLanguageRepository
import dev.kyralabs.pingogram.language.contextInLanguage
import dev.kyralabs.pingogram.language.inAppLanguageOf
import dev.kyralabs.pingogram.link.LinkViewModel
import dev.kyralabs.pingogram.link.linkOf
import dev.kyralabs.pingogram.share.isOwnContent
import dev.kyralabs.pingogram.share.shareOf
import dev.kyralabs.pingogram.startup.AwaitSettingsTrace
import dev.kyralabs.pingogram.startup.StartupTrace
import dev.kyralabs.pingogram.ui.LockableApp
import dev.kyralabs.pingogram.ui.PingogramApp
import dev.kyralabs.pingogram.ui.rememberLinkUriHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The only activity. Draws edge to edge behind transparent system bars; Compose handles the insets
 * (`REQUIREMENTS.md` §7). The splash screen stays until TDLib reports whether the account is logged in and the
 * Appearance settings are read, for at most 300 ms (`docs/PERFORMANCE.md` §2).
 *
 * The theme follows the Appearance settings ([MainActivityViewModel.appearance]), and so do the system bars' icons:
 * light on a dark theme, dark on a light one, whatever the system's own setting. Nothing is composed until the settings
 * are read, so the app never draws in a theme the user did not choose; the splash screen covers that wait. It then
 * fades out over the app as its icon grows ([animateExit]). On Android 13+ the next start's splash screen takes the
 * background of the theme the settings resolve to ([splashScreenThemeOf]); the system keeps it, so it is set off the
 * main thread whenever it changes.
 *
 * The app's language (Settings → Language) is Android's from Android 13 on, which creates the activity again when it
 * changes. Before, the activity takes it itself: its context is in the language chosen when it is attached
 * ([attachBaseContext], [contextInLanguage]); a language read only later (the first activity of a process, a few
 * milliseconds after it starts) or changed in Settings creates it again ([recreate]) before anything is composed in
 * another language.
 *
 * A notification or a conversation shortcut opens a chat through the intent that starts the activity, or, when it runs
 * already, through [onNewIntent] ([NotificationIntents.chatIdOf]). A restored activity (rotation, process death) does
 * not act on its old intent again: its restored back stack shows the chat already.
 *
 * A Telegram link another app opens (`tg://`, `https://t.me/…`, the manifest's intent filters,
 * `docs/adr/0013-deep-links.md`) arrives the same way ([linkOf]) and opens through [links] once the account is logged
 * in and the app unlocked; signed out, after the login. The activity is `singleTask`, so a link from another app's task
 * opens in Pingogram's own task and window, through [onNewIntent] when it runs, rather than in a second copy of the app
 * above the other app. Reopened from the recent apps, its launching intent is not handled again.
 *
 * What another app shares with Pingogram (`ACTION_SEND`, `ACTION_SEND_MULTIPLE`; `REQUIREMENTS.md` FR-N2) arrives the
 * same way ([shareOf]) and opens once logged in and unlocked, as a link: the share sheet to pick chats, or, from a
 * Direct Share target, that chat with the share in its composer ([shares]). Its files are read later with the grant
 * they came with, which lasts while this activity's task does.
 *
 * The passcode lock (`REQUIREMENTS.md` FR-S1, [MainActivityViewModel.lock]) shows its lock screen instead of the app
 * ([LockableApp]); a chat a notification asked for opens only once it is unlocked. The activity tells the lock when the
 * app leaves the screen and comes back ([onStop], [onStart]), but not for a configuration change. While locked, the
 * window is secure (no screenshots, no screen recording of the passcode entered); "Hide content in recent apps" keeps
 * the content out of the recent apps: through [setRecentsScreenshotEnabled] on Android 13 and later, which still
 * allows screenshots, and with [WindowManager.LayoutParams.FLAG_SECURE] before, which does not. Screens ask for a
 * secure window too, through [LocalSecureWindow] (an open secret chat, `REQUIREMENTS.md` FR-E1); the window is secure
 * while either the lock or a screen needs it, and only [SecureWindowEffect] sets or clears the flag.
 *
 * Animated stickers play through the app's one [AnimatedStickers] ([LocalAnimatedStickers]).
 *
 * Test tags show as resource ids, so the Macrobenchmarks and the Baseline Profile generator (`:benchmark`) find the
 * lists they scroll with UiAutomator (`docs/PERFORMANCE.md` §9).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    /** Opens Telegram links, from intents and tapped in the app; the activity's, so a lookup survives rotation. */
    private val links: LinkViewModel by viewModels()

    /** Hands what another app shared to the share sheet or a chat's composer. */
    @Inject
    lateinit var shares: ShareInbox

    /** Plays animated stickers in the conversation and the sticker panel ([LocalAnimatedStickers]). */
    @Inject
    lateinit var animatedStickers: AnimatedStickers

    /** What the call buttons call through ([LocalCallStarter]): `feature:calls`' call screen. */
    @Inject
    lateinit var calls: Calls

    /** Whether phone numbers are hidden on screen ([LocalPhoneNumbersHidden], Privacy and security). */
    @Inject
    lateinit var phoneNumberVisibility: PhoneNumberVisibilityRepository

    /** The chats kept behind the passcode, which the conversation's gate reads ([LocalLockedChatGate]). */
    @Inject
    lateinit var chatLock: ChatLockRepository

    @Inject
    @Dispatcher(IO)
    lateinit var io: CoroutineDispatcher

    @Inject
    lateinit var appLanguageRepository: PlatformAppLanguageRepository

    /** From [onCreate] until the first frame's settings are read ([StartupTrace.AWAIT_SETTINGS]). */
    private val awaitSettingsTrace = AwaitSettingsTrace()

    /** Before Android 13: the language [attachBaseContext] gave this activity, null for the system's. */
    private var attachedLanguageTag: String? = null

    /** Whether the activity is in the app's language; before Android 13, false until the language is read. */
    private lateinit var languageApplied: StateFlow<Boolean>

    override fun attachBaseContext(newBase: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.attachBaseContext(newBase)
        } else {
            // Not read yet: the system's languages, which the activity keeps if no other language was chosen.
            val language = inAppLanguageOf(newBase)
            attachedLanguageTag = language?.languageTag
            super.attachBaseContext(contextInLanguage(newBase, language))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) =
        StartupTrace.section(StartupTrace.ACTIVITY_CREATE) {
            awaitSettingsTrace.begin()
            // Before super.onCreate: switches from Theme.Pingogram.Starting to the post-splash theme.
            val splashScreen = installSplashScreen()
            super.onCreate(savedInstanceState)
            // The bound reference creates the view model now, which starts listening to TDLib, reading the settings and
            // the splash time limit.
            splashScreen.setKeepOnScreenCondition(viewModel::keepSplashScreen)
            splashScreen.setOnExitAnimationListener { it.animateExit(lifecycleScope) }
            enableEdgeToEdge()
            if (savedInstanceState == null) handle(intent)
            languageApplied = followAppLanguage()
            setContent {
                val appearance by viewModel.appearance.collectAsStateWithLifecycle()
                val lock by viewModel.lock.collectAsStateWithLifecycle()
                val inAppLanguage by languageApplied.collectAsStateWithLifecycle()
                // Read with the Appearance settings, from the same file: waiting for it costs nothing, and a value that
                // changed after the first frame would compose the whole app again (a static composition local).
                val phoneNumbersHiddenSetting by phoneNumberVisibility.hidden
                    .collectAsStateWithLifecycle(initialValue = null)
                val settings = appearance
                val phoneNumbersHidden = phoneNumbersHiddenSetting
                val lockAndLanguageKnown = lock != LockState.Unknown && inAppLanguage
                if (settings != null && phoneNumbersHidden != null && lockAndLanguageKnown) {
                    SideEffect(awaitSettingsTrace::end)
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    val hideContent by viewModel.hideContent.collectAsStateWithLifecycle()
                    val locked = lock == LockState.Locked
                    val secureRequests = remember { SecureWindowRequests() }
                    val lockedChatGate = remember { lockedChatGate(chatLock.hiddenChatIds) }
                    SecureWindowEffect(locked = locked, hideContent = hideContent, requests = secureRequests)
                    AppTheme(settings) {
                        CompositionLocalProvider(
                            LocalAnimatedStickers provides animatedStickers,
                            LocalSecureWindow provides secureRequests,
                            // Telegram links tapped in the app open in it (LinkUriHandler).
                            LocalUriHandler provides rememberLinkUriHandler(links, uiState),
                            // The conversation's and the profile's call buttons, and "Call back" (feature:calls).
                            LocalCallStarter provides calls,
                            // A chat's voice chat bar and its info page's voice chat row (feature:calls).
                            LocalVoiceChats provides calls,
                            LocalPhoneNumbersHidden provides phoneNumbersHidden,
                            // A locked chat asks for the passcode before it shows (docs/adr/0029-locked-chats.md).
                            LocalLockedChatGate provides lockedChatGate,
                        ) {
                            LockableApp(
                                locked = locked,
                                lockScreen = { AppLockRoute() },
                                modifier = Modifier.semantics { testTagsAsResourceId = true },
                            ) { App(uiState, viewModel, links, shares) }
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
     * Keeps the window secure while [locked], while a screen [requests] it (a secret chat), or, before Android 13,
     * while [hideContent] (the only way to keep the content out of the recent apps there), and from Android 13 on keeps
     * the content out of the recent apps with [setRecentsScreenshotEnabled] while [hideContent]. It reads [requests]
     * itself, so a request recomposes only this effect.
     */
    @Composable
    private fun SecureWindowEffect(
        locked: Boolean,
        hideContent: Boolean,
        requests: SecureWindowRequests,
    ) {
        val recentsScreenshots = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val secure = locked || requests.isRequested || (hideContent && !recentsScreenshots)
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
        handle(intent)
    }

    /**
     * Before Android 13, creates the activity again once the app's language differs from the one it was attached in,
     * and returns whether it is in it; from Android 13 on, Android does both.
     */
    private fun followAppLanguage(): StateFlow<Boolean> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return MutableStateFlow(true)
        val inAppLanguage = appLanguageRepository.inAppLanguage
        lifecycleScope.launch {
            inAppLanguage.filterNotNull().first { it.languageTag != attachedLanguageTag }
            recreate()
        }
        return inAppLanguage
            .map { it != null && it.languageTag == attachedLanguageTag }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, initialValue = false)
    }

    /**
     * Opens the chat [intent] names, if it is a notification's or a conversation shortcut's, the Telegram link it
     * views, or what it shares.
     */
    private fun handle(intent: Intent?) {
        NotificationIntents.chatIdOf(intent)?.let(viewModel::openChat)
        if (NotificationIntents.opensLockedChats(intent)) viewModel.setLockedChatsToOpen(true)
        linkOf(intent)?.let(viewModel::openLink)
        shareOf(intent) { isOwnContent(it) }?.let(viewModel::openShare)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val splashScreenTheme = splashScreenThemeOf(settings)
            LaunchedEffect(splashScreenTheme) { persistSplashScreenTheme(splashScreenTheme) }
        }
        PingogramTheme(
            themeMode = settings.themeMode,
            dynamicColor = settings.dynamicColor,
            palette = settings.palette,
            contrast = settings.contrast,
            amoled = settings.amoled,
            chat = settings.chat,
            content = content,
        )
    }

    /** Sets the splash screen theme of the next starts; the system keeps it, through a call to it. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun persistSplashScreenTheme(
        @StyleRes theme: Int,
    ) = withContext(io) { splashScreen.setSplashScreenTheme(theme) }

    private companion object {
        // enableEdgeToEdge()'s own scrims behind three-button navigation where the system draws none (before Android
        // 10), which androidx.activity does not expose: the platform's light and dark navigation bar scrims.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}

/** `core:ui`'s [LocalLockedChatGate] over [hiddenChatIds]: `feature:settings`' [LockedChatGate]. */
private fun lockedChatGate(hiddenChatIds: StateFlow<Set<Long>?>): @Composable (Long, @Composable () -> Unit) -> Unit =
    { chatId, content -> LockedChatGate(hiddenChatIds, chatId, content) }

/** The app, with what a notification, a link or another app asked to open ([MainActivityViewModel]). */
@Composable
private fun App(
    uiState: MainActivityUiState,
    viewModel: MainActivityViewModel,
    links: LinkViewModel,
    shares: ShareInbox,
) {
    val chatToOpen by viewModel.chatToOpen.collectAsStateWithLifecycle()
    val lockedChatsToOpen by viewModel.lockedChatsToOpen.collectAsStateWithLifecycle()
    val linkToOpen by viewModel.linkToOpen.collectAsStateWithLifecycle()
    val shareToOpen by viewModel.shareToOpen.collectAsStateWithLifecycle()
    PingogramApp(
        uiState = uiState,
        chatToOpen = chatToOpen,
        onChatOpened = viewModel::onChatOpened,
        lockedChatsToOpen = lockedChatsToOpen,
        onLockedChatsOpened = { viewModel.setLockedChatsToOpen(false) },
        links = links,
        linkToOpen = linkToOpen,
        onLinkOpened = viewModel::onLinkOpened,
        shares = shares,
        shareToOpen = shareToOpen,
        onShareOpened = viewModel::onShareOpened,
    )
}
