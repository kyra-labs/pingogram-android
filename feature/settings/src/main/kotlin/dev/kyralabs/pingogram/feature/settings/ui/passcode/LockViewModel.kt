package dev.kyralabs.pingogram.feature.settings.ui.passcode

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.PasscodeSettings
import dev.kyralabs.pingogram.core.domain.repository.AppLockRepository
import dev.kyralabs.pingogram.core.domain.repository.LockState
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the lock screen shows.
 *
 * @property entry the passcode entry, for the passcode's type.
 * @property biometricUnlock whether the fingerprint button shows: biometric unlock (or unlocking with the screen lock)
 *   is on and the device can do it.
 * @property screenLock what the system's prompt takes of the phone's screen lock.
 * @property promptBiometrics whether the system's biometric prompt should show by itself, once the screen is in front:
 *   the first time after each lock.
 * @property showForgetDialog whether "Forgot passcode?" asks to log out.
 * @property isLoggingOut whether logging out for a forgotten passcode is under way.
 */
@Immutable
data class LockUiState(
    val entry: PasscodeEntryUiState = PasscodeEntryUiState(),
    val biometricUnlock: Boolean = false,
    val promptBiometrics: Boolean = false,
    val showForgetDialog: Boolean = false,
    val isLoggingOut: Boolean = false,
    val screenLock: ScreenLock = ScreenLock.Off,
)

/** What the user does on the lock screen, handled by [LockViewModel.onEvent]. */
sealed interface LockEvent {
    data object Submit : LockEvent

    /** The system's biometric prompt was shown by itself, so it does not show again until the next lock. */
    data object BiometricPromptShown : LockEvent

    data object BiometricSucceeded : LockEvent

    data object ForgotPasscode : LockEvent

    data object ConfirmForget : LockEvent

    data object DismissForget : LockEvent
}

/** One-off side effects of [LockViewModel], handled once by the lock screen. */
sealed interface LockEffect {
    /** The passcode was right or the biometrics recognized: the lock opens ([HapticFeedbackType.Confirm]). */
    data object Unlocked : LockEffect

    /** Logging out for a forgotten passcode failed, shown in a snackbar. */
    data class ShowError(
        val error: DomainError,
    ) : LockEffect
}

/**
 * The lock screen (`REQUIREMENTS.md` FR-S1): unlocks the app ([AppLockRepository]) with the passcode, typed into
 * [input], or with biometrics where they are on ([BiometricAvailability]), and logs out for a forgotten passcode.
 * Wrong passcodes make the next attempts wait, which the entry counts down.
 *
 * The activity keeps it while the app is locked and unlocked again, so every new lock starts it over: empty, and
 * offering the biometric prompt again. The passcode entered never leaves memory.
 */
@HiltViewModel
class LockViewModel
    @Inject
    constructor(
        private val appLock: AppLockRepository,
        passcodeRepository: PasscodeRepository,
        private val biometrics: BiometricAvailability,
    ) : ViewModel() {
        private val checker =
            PasscodeChecker(
                scope = viewModelScope,
                check = appLock::unlock,
                remainingWait = appLock::remainingWait,
                onCorrect = { effectChannel.trySend(LockEffect.Unlocked) },
            )

        /** The passcode being entered. */
        val input: PasscodeInput get() = checker.input

        private val local = MutableStateFlow(LocalState())
        private val effectChannel = Channel<LockEffect>(Channel.BUFFERED)
        val effects: Flow<LockEffect> = effectChannel.receiveAsFlow()

        private var settings = PasscodeSettings()

        val uiState: StateFlow<LockUiState> =
            combine(checker.entry, local) { entry, local ->
                LockUiState(
                    entry = entry,
                    biometricUnlock = local.biometricUnlock,
                    promptBiometrics = local.promptBiometrics,
                    showForgetDialog = local.showForgetDialog,
                    isLoggingOut = local.isLoggingOut,
                    screenLock = biometrics.screenLockFor(settings),
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, LockUiState())

        init {
            viewModelScope.launch {
                passcodeRepository.settings.collect { settings ->
                    this@LockViewModel.settings = settings
                    checker.setType(settings.type)
                    val biometricUnlock = canUseBiometrics()
                    local.update {
                        it.copy(
                            biometricUnlock = biometricUnlock,
                            promptBiometrics =
                                it.promptBiometrics && biometricUnlock,
                        )
                    }
                }
            }
            viewModelScope.launch {
                appLock.state.collect { state ->
                    if (state == LockState.Locked) startOver()
                }
            }
        }

        fun onEvent(event: LockEvent) {
            when (event) {
                LockEvent.Submit -> {
                    checker.submit()
                }

                LockEvent.BiometricPromptShown -> {
                    local.update { it.copy(promptBiometrics = false) }
                }

                LockEvent.BiometricSucceeded -> {
                    viewModelScope.launch {
                        appLock.unlockWithBiometrics()
                        effectChannel.trySend(LockEffect.Unlocked)
                    }
                }

                LockEvent.ForgotPasscode -> {
                    local.update { it.copy(showForgetDialog = true) }
                }

                LockEvent.DismissForget -> {
                    local.update { it.copy(showForgetDialog = false) }
                }

                LockEvent.ConfirmForget -> {
                    forget()
                }
            }
        }

        private fun startOver() {
            checker.reset()
            val biometricUnlock = canUseBiometrics()
            local.value = LocalState(biometricUnlock = biometricUnlock, promptBiometrics = biometricUnlock)
        }

        private fun forget() {
            if (local.value.isLoggingOut) return
            local.update { it.copy(showForgetDialog = false, isLoggingOut = true) }
            viewModelScope.launch {
                val result = appLock.forgetPasscode()
                local.update { it.copy(isLoggingOut = false) }
                if (result is Outcome.Failure) effectChannel.trySend(LockEffect.ShowError(result.error))
            }
        }

        private fun canUseBiometrics(): Boolean = biometrics.canUnlock(settings)

        private data class LocalState(
            val biometricUnlock: Boolean = false,
            val promptBiometrics: Boolean = false,
            val showForgetDialog: Boolean = false,
            val isLoggingOut: Boolean = false,
        )
    }
