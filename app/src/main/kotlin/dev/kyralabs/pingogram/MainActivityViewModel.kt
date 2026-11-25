package dev.kyralabs.pingogram

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.repository.AppLockRepository
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.LockState
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import dev.kyralabs.pingogram.navigation.ChatToOpen
import dev.kyralabs.pingogram.share.IncomingShare
import dev.kyralabs.pingogram.startup.FirstFrameSettings
import dev.kyralabs.pingogram.startup.SplashStateTrace
import dev.kyralabs.pingogram.startup.StartupTrace.APPEARANCE_KNOWN
import dev.kyralabs.pingogram.startup.StartupTrace.LOCK_KNOWN
import dev.kyralabs.pingogram.startup.StartupTrace.LOGIN_KNOWN
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Whether the account is logged in ([uiState]), from TDLib's login steps; the Appearance settings the app's theme
 * follows ([appearance]); whether the passcode lock holds the app ([lock], [hideContent]); how long the splash screen
 * stays ([keepSplashScreen]); the chat a notification or a conversation shortcut asked to open ([chatToOpen]); the
 * Telegram link another app opened Pingogram with ([linkToOpen]); and what another app shared with it
 * ([shareToOpen]). Nothing to restore after process death: TDLib reports the current step again, the settings are
 * saved, a passcode locks the app at every start, the back stack restored after process death already holds an opened
 * chat or link, and a share's read grants end with the process.
 */
@HiltViewModel
class MainActivityViewModel internal constructor(
    private val authRepository: AuthRepository,
    appearance: StateFlow<AppearanceSettings?>,
    private val appLockRepository: AppLockRepository,
    passcodeRepository: PasscodeRepository,
    timeSource: TimeSource,
    private val chatLock: Lazy<ChatLockRepository>,
    private val splashTrace: SplashStateTrace = SplashStateTrace.None,
) : ViewModel() {
    // The locked chats are the shown account's, which the account registry names; asked for when the app first leaves
    // the screen, not while the activity is made, so its creation never waits for the registry's file
    // (docs/audit/performance-review-2026-11.md, W2).
    @Inject
    constructor(
        authRepository: AuthRepository,
        firstFrameSettings: FirstFrameSettings,
        appLockRepository: AppLockRepository,
        passcodeRepository: PasscodeRepository,
        chatLock: dagger.Lazy<ChatLockRepository>,
    ) : this(
        authRepository,
        firstFrameSettings.appearance,
        appLockRepository,
        passcodeRepository,
        TimeSource.Monotonic,
        lazy { chatLock.get() },
        SplashStateTrace.Platform(),
    )

    private val createdAt = timeSource.markNow()

    private val mutableUiState = MutableStateFlow<MainActivityUiState>(MainActivityUiState.Loading)

    /** Whether the account is logged in, from the login steps or, for the splash screen, TDLib's current step. */
    val uiState: StateFlow<MainActivityUiState> = mutableUiState.asStateFlow()

    /**
     * The Appearance settings, then every change; null until they are read, once per process ([FirstFrameSettings]),
     * so a view model made after the first already has them. Not part of [uiState]: the theme around the whole app
     * follows it, not one screen.
     */
    val appearance: StateFlow<AppearanceSettings?> = appearance

    /**
     * Whether the passcode lock holds the app: [LockState.Unknown] until its settings are read (with the Appearance
     * settings, from the same file), then whether the lock screen shows instead of the app (`REQUIREMENTS.md` FR-S1).
     */
    val lock: StateFlow<LockState> = appLockRepository.state

    init {
        listOf(LOGIN_KNOWN, APPEARANCE_KNOWN, LOCK_KNOWN).forEach(splashTrace::begin)
        // Before the activity's first composition, which comes before the splash screen first asks: where TDLib already
        // knows the step, as in a warm start, the first frame shows the app instead of progress.
        mutableUiState.takeCurrentStep(authRepository)
        viewModelScope.launch {
            authRepository.authStep.mapNotNull { it.toMainActivityUiState() }.collect { mutableUiState.value = it }
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            uiState.first { it != MainActivityUiState.Loading }
            splashTrace.end(LOGIN_KNOWN)
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            appearance.first { it != null }
            splashTrace.end(APPEARANCE_KNOWN)
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            lock.first { it != LockState.Unknown }
            splashTrace.end(LOCK_KNOWN)
        }
    }

    /** Whether the app's content stays out of the recent apps: a passcode is set, with "Hide content" on. */
    val hideContent: StateFlow<Boolean> =
        passcodeRepository.settings
            .map { it.isSet && it.hideContent }
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    private val mutableChatToOpen = MutableStateFlow<ChatToOpen?>(null)

    /**
     * The chat a notification or a conversation shortcut asked to open, until the app opened it ([onChatOpened]):
     * once the account is known to be logged in, and never in the login flow.
     */
    val chatToOpen: StateFlow<ChatToOpen?> = mutableChatToOpen.asStateFlow()

    /** Asks to open chat [chatId], at [messageId] where named; a later request replaces one not opened yet. */
    fun openChat(
        chatId: Long,
        messageId: Long? = null,
    ) {
        mutableChatToOpen.value = ChatToOpen(chatId, messageId)
    }

    /** The chat of [chatToOpen] was opened, or dropped because nobody is logged in. */
    fun onChatOpened() {
        mutableChatToOpen.value = null
    }

    private val mutablePageToOpen = MutableStateFlow<PageToOpen?>(null)

    /**
     * A page something outside the app asked to open, until the app opened it, or dropped it because nobody is logged
     * in ([setPageToOpen] null): Locked chats, which a locked chat's notification opens
     * (`docs/adr/0029-locked-chats.md`), Focus, which its Quick Settings tile opens (board 31), or a Conversation
     * widget's chat picker or reply sheet (board 33); like [chatToOpen], once logged in and never in the login flow.
     */
    val pageToOpen: StateFlow<PageToOpen?> = mutablePageToOpen.asStateFlow()

    fun setPageToOpen(page: PageToOpen?) {
        mutablePageToOpen.value = page
    }

    private val mutableLinkToOpen = MutableStateFlow<String?>(null)

    /**
     * The Telegram link another app opened Pingogram with (`docs/adr/0013-deep-links.md`), until the app took it
     * ([onLinkOpened]): once the account is logged in and the app unlocked. A link belongs to no account, so unlike
     * [chatToOpen] it waits through the login flow and opens after logging in.
     */
    val linkToOpen: StateFlow<String?> = mutableLinkToOpen.asStateFlow()

    /** Asks to open [link]; a later link replaces one not opened yet. */
    fun openLink(link: String) {
        mutableLinkToOpen.value = link
    }

    /** The link of [linkToOpen] was handed over to be opened. */
    fun onLinkOpened() {
        mutableLinkToOpen.value = null
    }

    private val mutableShareToOpen = MutableStateFlow<IncomingShare?>(null)

    /**
     * What another app shared with Pingogram (`REQUIREMENTS.md` FR-N2, direct share), until the app took it
     * ([onShareOpened]): once the account is logged in and the app unlocked, as a link. Like a link it waits through
     * the login flow; nothing of it is sent before the user picks a chat and taps send.
     */
    val shareToOpen: StateFlow<IncomingShare?> = mutableShareToOpen.asStateFlow()

    /** Asks to open [share]; a later share replaces one not opened yet. */
    fun openShare(share: IncomingShare) {
        mutableShareToOpen.value = share
    }

    /** The share of [shareToOpen] was handed over to be opened. */
    fun onShareOpened() {
        mutableShareToOpen.value = null
    }

    /**
     * The app left the screen, not for a configuration change: the passcode lock's auto-lock starts counting, and so
     * does the locked chats' ([ChatLockRepository]).
     */
    fun onAppHidden() {
        appLockRepository.onAppHidden()
        chatLock.value.onAppHidden()
    }

    /** The app is on screen again: it locks if it was away for the auto-lock delay, the locked chats after a while. */
    fun onAppShown() {
        appLockRepository.onAppShown()
        chatLock.value.onAppShown()
    }

    /**
     * Whether the splash screen stays on screen: while the login state, the Appearance settings or the lock are
     * unknown, but no longer than [SPLASH_SCREEN_TIMEOUT] after this view model was created (`docs/PERFORMANCE.md` §2).
     * After that, `PingogramApp` shows progress for a login state still unknown, and the window's background stands in
     * for settings still being read. The splash screen asks before every frame it holds back.
     *
     * While [AuthRepository.authStep] has not brought the login state, it takes TDLib's current step: the steps come
     * through the update router and the `Default` dispatcher, which the rest of a cold start keeps busy. Only
     * `Initializing`, which changes nothing, comes before TDLib's first step, so the steps that follow agree with it.
     */
    fun keepSplashScreen(): Boolean {
        mutableUiState.takeCurrentStep(authRepository)
        val unknown =
            uiState.value == MainActivityUiState.Loading || appearance.value == null || lock.value == LockState.Unknown
        return unknown && createdAt.elapsedNow() < SPLASH_SCREEN_TIMEOUT
    }

    internal companion object {
        val SPLASH_SCREEN_TIMEOUT = 300.milliseconds
    }
}

/**
 * While the login state is unknown, takes [authRepository]'s current step, as [MainActivityViewModel.keepSplashScreen]
 * explains.
 */
private fun MutableStateFlow<MainActivityUiState>.takeCurrentStep(authRepository: AuthRepository) {
    if (value != MainActivityUiState.Loading) return
    authRepository.currentStep().toMainActivityUiState()?.let { compareAndSet(MainActivityUiState.Loading, it) }
}

/** A page something outside the app asks to open ([MainActivityViewModel.pageToOpen]). */
sealed interface PageToOpen {
    /** Locked chats, from a locked chat's notification. */
    data object LockedChats : PageToOpen

    /** The Chats tab with its Focus switcher, from the Focus tile's long press. */
    data object FocusSwitcher : PageToOpen

    /** Settings → Focus, from the Focus tile while there is no profile to turn on. */
    data object Focus : PageToOpen

    /** The chat picker of the Conversation widget [appWidgetId], from its "Choose a chat" (board 33). */
    data class WidgetChat(
        val appWidgetId: Int,
    ) : PageToOpen

    /** The reply sheet of chat [chatId], from a Conversation widget's Reply (board 33), in the chat's account. */
    data class WidgetReply(
        val chatId: Long,
    ) : PageToOpen
}
