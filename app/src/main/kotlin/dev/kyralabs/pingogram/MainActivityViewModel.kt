package dev.kyralabs.pingogram

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.repository.AppLockRepository
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.LockState
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
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
 * stays ([keepSplashScreen]); and the chat a notification or a conversation shortcut asked to open ([chatToOpen]).
 * Nothing to restore after process death: TDLib reports the current step again, the settings are saved, a passcode
 * locks the app at every start, and the back stack restored after process death already holds an opened chat.
 */
@HiltViewModel
class MainActivityViewModel internal constructor(
    authRepository: AuthRepository,
    appearanceRepository: AppearanceRepository,
    private val appLockRepository: AppLockRepository,
    passcodeRepository: PasscodeRepository,
    timeSource: TimeSource,
) : ViewModel() {
    @Inject
    constructor(
        authRepository: AuthRepository,
        appearanceRepository: AppearanceRepository,
        appLockRepository: AppLockRepository,
        passcodeRepository: PasscodeRepository,
    ) : this(authRepository, appearanceRepository, appLockRepository, passcodeRepository, TimeSource.Monotonic)

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

    /** The app left the screen, not for a configuration change: the passcode lock's auto-lock starts counting. */
    fun onAppHidden() = appLockRepository.onAppHidden()

    /** The app is on screen again: it locks if it was away for the auto-lock delay. */
    fun onAppShown() = appLockRepository.onAppShown()

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
