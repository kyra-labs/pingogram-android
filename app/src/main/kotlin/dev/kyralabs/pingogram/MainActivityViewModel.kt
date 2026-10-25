package dev.kyralabs.pingogram

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.repository.AppLockRepository
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.LockState
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import dev.kyralabs.pingogram.share.IncomingShare
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
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
    authRepository: AuthRepository,
    appearanceRepository: AppearanceRepository,
    private val appLockRepository: AppLockRepository,
    passcodeRepository: PasscodeRepository,
    timeSource: TimeSource,
    private val chatLock: ChatLockRepository,
) : ViewModel() {
    @Inject
    constructor(
        authRepository: AuthRepository,
        appearanceRepository: AppearanceRepository,
        appLockRepository: AppLockRepository,
        passcodeRepository: PasscodeRepository,
        chatLock: ChatLockRepository,
    ) : this(
        authRepository,
        appearanceRepository,
        appLockRepository,
        passcodeRepository,
        TimeSource.Monotonic,
        chatLock,
    )

    private val createdAt = timeSource.markNow()

    val uiState: StateFlow<MainActivityUiState> =
        authRepository.authStep
            .mapNotNull { it.toMainActivityUiState() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, MainActivityUiState.Loading)

    /**
     * The Appearance settings, then every change; null until they are read, which takes a few milliseconds on the IO
     * dispatcher, started when this view model is created. Not part of [uiState]: the theme around the whole app
     * follows it, not one screen.
     */
    val appearance: StateFlow<AppearanceSettings?> =
        appearanceRepository.appearance.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    /**
     * Whether the passcode lock holds the app: [LockState.Unknown] until its settings are read (with the Appearance
     * settings, from the same file), then whether the lock screen shows instead of the app (`REQUIREMENTS.md` FR-S1).
     */
    val lock: StateFlow<LockState> = appLockRepository.state

    /** Whether the app's content stays out of the recent apps: a passcode is set, with "Hide content" on. */
    val hideContent: StateFlow<Boolean> =
        passcodeRepository.settings
            .map { it.isSet && it.hideContent }
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    private val mutableChatToOpen = MutableStateFlow<Long?>(null)

    /**
     * The chat a notification or a conversation shortcut asked to open, until the app opened it ([onChatOpened]):
     * once the account is known to be logged in, and never in the login flow.
     */
    val chatToOpen: StateFlow<Long?> = mutableChatToOpen.asStateFlow()

    /** Asks to open chat [chatId]; a later request replaces one not opened yet. */
    fun openChat(chatId: Long) {
        mutableChatToOpen.value = chatId
    }

    /** The chat of [chatToOpen] was opened, or dropped because nobody is logged in. */
    fun onChatOpened() {
        mutableChatToOpen.value = null
    }

    private val mutableLockedChatsToOpen = MutableStateFlow(false)

    /**
     * Whether a locked chat's notification asked to open Locked chats (`docs/adr/0029-locked-chats.md`), until the app
     * opened them ([setLockedChatsToOpen] false); like [chatToOpen], once logged in and never in the login flow.
     */
    val lockedChatsToOpen: StateFlow<Boolean> = mutableLockedChatsToOpen.asStateFlow()

    /** Asks to open Locked chats ([open]), or reports them opened. */
    fun setLockedChatsToOpen(open: Boolean) {
        mutableLockedChatsToOpen.value = open
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
        chatLock.onAppHidden()
    }

    /** The app is on screen again: it locks if it was away for the auto-lock delay, the locked chats after a while. */
    fun onAppShown() {
        appLockRepository.onAppShown()
        chatLock.onAppShown()
    }

    /**
     * Whether the splash screen stays on screen: while the login state, the Appearance settings or the lock are
     * unknown, but no longer than [SPLASH_SCREEN_TIMEOUT] after this view model was created (`docs/PERFORMANCE.md` §2).
     * After that, `PingogramApp` shows progress for a login state still unknown, and the window's background stands in
     * for settings still being read. The splash screen asks before every frame it holds back.
     */
    fun keepSplashScreen(): Boolean {
        val unknown =
            uiState.value == MainActivityUiState.Loading || appearance.value == null || lock.value == LockState.Unknown
        return unknown && createdAt.elapsedNow() < SPLASH_SCREEN_TIMEOUT
    }

    internal companion object {
        val SPLASH_SCREEN_TIMEOUT = 300.milliseconds
    }
}
