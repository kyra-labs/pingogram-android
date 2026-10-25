package dev.kyralabs.pingogram.feature.settings.ui.passcode

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AutoLock
import dev.kyralabs.pingogram.core.domain.model.PasscodeSettings
import dev.kyralabs.pingogram.core.domain.repository.AppLockRepository
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

/** What the Passcode lock page shows (`REQUIREMENTS.md` FR-S1). */
@Immutable
sealed interface PasscodeLockUiState {
    /** The settings are being read. */
    data object Loading : PasscodeLockUiState

    /** No passcode: what the lock does, and turning it on. */
    data object Off : PasscodeLockUiState

    /** A passcode is set, and must be entered before its settings show, so nobody else changes them. */
    data class Verify(
        val entry: PasscodeEntryUiState,
    ) : PasscodeLockUiState

    /**
     * A passcode is set, and was entered here.
     *
     * @property autoLock how long the app may stay out of sight before it locks.
     * @property biometricAvailable whether the device can unlock with biometrics, which shows their switch.
     * @property biometricUnlock whether biometric unlock is on.
     * @property screenLockAvailable whether the phone's screen lock can unlock (Android 11 and later, a screen lock
     *   set), which shows its switch.
     * @property screenLockUnlock whether unlocking with the phone's screen lock is on.
     * @property hideContent whether the app's content stays hidden in recent apps.
     * @property showTurnOffDialog whether turning the passcode off asks first.
     */
    data class On(
        val autoLock: AutoLock,
        val biometricAvailable: Boolean,
        val biometricUnlock: Boolean,
        val hideContent: Boolean,
        val screenLockAvailable: Boolean = false,
        val screenLockUnlock: Boolean = false,
        val showTurnOffDialog: Boolean = false,
    ) : PasscodeLockUiState
}

/** What the user does on the Passcode lock page, handled by [PasscodeLockViewModel.onEvent]. */
sealed interface PasscodeLockEvent {
    /** The page shows again, e.g. back from the device's settings: biometrics may have been set up meanwhile. */
    data object Refresh : PasscodeLockEvent

    /** Submits the passcode entered to open the settings. */
    data object Submit : PasscodeLockEvent

    data class SelectAutoLock(
        val autoLock: AutoLock,
    ) : PasscodeLockEvent

    data class SetScreenLockUnlock(
        val enabled: Boolean,
    ) : PasscodeLockEvent

    data class SetBiometricUnlock(
        val enabled: Boolean,
    ) : PasscodeLockEvent

    data class SetHideContent(
        val enabled: Boolean,
    ) : PasscodeLockEvent

    data object TurnOff : PasscodeLockEvent

    data object ConfirmTurnOff : PasscodeLockEvent

    data object DismissTurnOff : PasscodeLockEvent
}

/** One-off side effects of [PasscodeLockViewModel], handled once by the page. */
sealed interface PasscodeLockEffect {
    /** A setting could not be saved, shown in a snackbar. */
    data class ShowError(
        val error: DomainError,
    ) : PasscodeLockEffect
}

/**
 * The Passcode lock page (`REQUIREMENTS.md` FR-S1): turning the passcode lock on (setting a passcode is a page of its
 * own) and, once the passcode was entered here, its settings: auto-lock, biometric unlock, hiding the content in
 * recent apps, changing the passcode, and turning it off. Changes made anywhere show at once ([PasscodeRepository]'s
 * settings flow). The passcode entered here counts as an attempt, as on the lock screen ([AppLockRepository]).
 *
 * Without a passcode when the page opens, the passcode set from it needs no entering here.
 */
@HiltViewModel
class PasscodeLockViewModel
    @Inject
    constructor(
        private val passcodeRepository: PasscodeRepository,
        appLock: AppLockRepository,
        private val biometrics: BiometricAvailability,
    ) : ViewModel() {
        private val checker =
            PasscodeChecker(
                scope = viewModelScope,
                check = appLock::checkPasscode,
                remainingWait = appLock::remainingWait,
                onCorrect = { local.update { it.copy(verified = true) } },
            )

        /** The passcode being entered to open the settings. */
        val input: PasscodeInput get() = checker.input

        private val local = MutableStateFlow(LocalState())
        private val effectChannel = Channel<PasscodeLockEffect>(Channel.BUFFERED)
        val effects: Flow<PasscodeLockEffect> = effectChannel.receiveAsFlow()

        val uiState: StateFlow<PasscodeLockUiState> =
            combine(passcodeRepository.settings, checker.entry, local) { settings, entry, local ->
                uiState(settings, entry, local)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, PasscodeLockUiState.Loading)

        init {
            viewModelScope.launch {
                passcodeRepository.settings.collect { settings ->
                    checker.setType(settings.type)
                    // Without a passcode at first, the one set from here needs no entering.
                    local.update { if (it.verified == null) it.copy(verified = !settings.isSet) else it }
                }
            }
        }

        fun onEvent(event: PasscodeLockEvent) {
            when (event) {
                PasscodeLockEvent.Refresh -> local.update { it.copy(refreshes = it.refreshes + 1) }
                PasscodeLockEvent.Submit -> checker.submit()
                is PasscodeLockEvent.SelectAutoLock -> save { setAutoLock(event.autoLock) }
                is PasscodeLockEvent.SetBiometricUnlock -> save { setBiometricUnlock(event.enabled) }
                is PasscodeLockEvent.SetScreenLockUnlock -> save { setDeviceCredentialUnlock(event.enabled) }
                is PasscodeLockEvent.SetHideContent -> save { setHideContent(event.enabled) }
                PasscodeLockEvent.TurnOff -> local.update { it.copy(showTurnOffDialog = true) }
                PasscodeLockEvent.DismissTurnOff -> local.update { it.copy(showTurnOffDialog = false) }
                PasscodeLockEvent.ConfirmTurnOff -> turnOff()
            }
        }

        private fun uiState(
            settings: PasscodeSettings,
            entry: PasscodeEntryUiState,
            local: LocalState,
        ): PasscodeLockUiState =
            when {
                local.verified == null -> {
                    PasscodeLockUiState.Loading
                }

                !settings.isSet -> {
                    PasscodeLockUiState.Off
                }

                !local.verified -> {
                    PasscodeLockUiState.Verify(entry)
                }

                else -> {
                    PasscodeLockUiState.On(
                        autoLock = settings.autoLock,
                        biometricAvailable = biometrics.canAuthenticate(),
                        biometricUnlock = settings.biometricUnlock,
                        hideContent = settings.hideContent,
                        showTurnOffDialog = local.showTurnOffDialog,
                        screenLockAvailable = biometrics.canUseScreenLock(),
                        screenLockUnlock = settings.deviceCredentialUnlock,
                    )
                }
            }

        private fun turnOff() {
            local.update { it.copy(showTurnOffDialog = false) }
            save { removePasscode() }
        }

        private fun save(change: suspend PasscodeRepository.() -> Outcome<Unit>) {
            viewModelScope.launch {
                val result = passcodeRepository.change()
                if (result is Outcome.Failure) effectChannel.trySend(PasscodeLockEffect.ShowError(result.error))
            }
        }

        /**
         * @property verified whether the passcode was entered here; null until the settings are read.
         * @property refreshes how often the page showed again, so the biometrics are checked anew.
         */
        private data class LocalState(
            val verified: Boolean? = null,
            val showTurnOffDialog: Boolean = false,
            val refreshes: Int = 0,
        )
    }
