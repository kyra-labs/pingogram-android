package dev.kyralabs.pingogram.feature.settings.ui.lockedchats

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.PasscodeSettings
import dev.kyralabs.pingogram.core.domain.repository.AppLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import dev.kyralabs.pingogram.feature.settings.ui.passcode.BiometricAvailability
import dev.kyralabs.pingogram.feature.settings.ui.passcode.PasscodeChecker
import dev.kyralabs.pingogram.feature.settings.ui.passcode.PasscodeEntryUiState
import dev.kyralabs.pingogram.feature.settings.ui.passcode.PasscodeInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the passcode entry before locked chats shows.
 *
 * @property entry the passcode entry, for the passcode's type.
 * @property biometricUnlock whether the fingerprint button shows: biometric unlock is on and the device can do it.
 * @property promptBiometrics whether the system's biometric prompt should show by itself, once the entry is in front:
 *   the first time after each lock.
 */
@Immutable
data class ChatUnlockUiState(
    val entry: PasscodeEntryUiState = PasscodeEntryUiState(),
    val biometricUnlock: Boolean = false,
    val promptBiometrics: Boolean = false,
)

/** What the user does on the passcode entry before locked chats, handled by [ChatUnlockViewModel.onEvent]. */
sealed interface ChatUnlockEvent {
    data object Submit : ChatUnlockEvent

    /** The system's biometric prompt was shown by itself, so it does not show again until the chats lock again. */
    data object BiometricPromptShown : ChatUnlockEvent

    data object BiometricSucceeded : ChatUnlockEvent
}

/** One-off side effects of [ChatUnlockViewModel]. */
sealed interface ChatUnlockEffect {
    /** The passcode was right or the biometrics recognized: the chats open ([HapticFeedbackType.Confirm]). */
    data object Unlocked : ChatUnlockEffect
}

/**
 * Opens the locked chats (`docs/adr/0029-locked-chats.md`) with the app's passcode, typed into [input], or with
 * biometrics where they are on: what a locked chat and the Locked chats page show until then. The attempts and the
 * waits after wrong ones are the app lock's ([ChatLockRepository.unlock]). Each time the chats lock again it starts
 * over, offering the biometric prompt again. The passcode entered never leaves memory.
 */
@HiltViewModel
class ChatUnlockViewModel
    @Inject
    constructor(
        private val chatLock: ChatLockRepository,
        appLock: AppLockRepository,
        passcodeRepository: PasscodeRepository,
        private val biometrics: BiometricAvailability,
    ) : ViewModel() {
        private val checker =
            PasscodeChecker(
                scope = viewModelScope,
                check = chatLock::unlock,
                remainingWait = appLock::remainingWait,
                onCorrect = { effectChannel.trySend(ChatUnlockEffect.Unlocked) },
            )

        /** The passcode being entered. */
        val input: PasscodeInput get() = checker.input

        private val local = MutableStateFlow(LocalState(promptBiometrics = true))
        private val effectChannel = Channel<ChatUnlockEffect>(Channel.BUFFERED)
        val effects: Flow<ChatUnlockEffect> = effectChannel.receiveAsFlow()

        private var settings = PasscodeSettings()

        val uiState: StateFlow<ChatUnlockUiState> =
            combine(checker.entry, local) { entry, local ->
                ChatUnlockUiState(entry, local.biometricUnlock, local.promptBiometrics)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatUnlockUiState())

        init {
            viewModelScope.launch {
                passcodeRepository.settings.collect { settings ->
                    this@ChatUnlockViewModel.settings = settings
                    checker.setType(settings.type)
                    val biometricUnlock = canUseBiometrics()
                    local.update {
                        it.copy(
                            biometricUnlock = biometricUnlock,
                            promptBiometrics = it.promptBiometrics && biometricUnlock,
                        )
                    }
                }
            }
            viewModelScope.launch {
                // Locked again after being opened: start over.
                chatLock.unlocked
                    .drop(1)
                    .filter { !it }
                    .collect { startOver() }
            }
        }

        fun onEvent(event: ChatUnlockEvent) {
            when (event) {
                ChatUnlockEvent.Submit -> {
                    checker.submit()
                }

                ChatUnlockEvent.BiometricPromptShown -> {
                    local.update { it.copy(promptBiometrics = false) }
                }

                ChatUnlockEvent.BiometricSucceeded -> {
                    viewModelScope.launch {
                        chatLock.unlockWithBiometrics()
                        effectChannel.trySend(ChatUnlockEffect.Unlocked)
                    }
                }
            }
        }

        private fun startOver() {
            checker.reset()
            val biometricUnlock = canUseBiometrics()
            local.value = LocalState(biometricUnlock = biometricUnlock, promptBiometrics = biometricUnlock)
        }

        private fun canUseBiometrics(): Boolean = settings.biometricUnlock && biometrics.canAuthenticate()

        private data class LocalState(
            val biometricUnlock: Boolean = false,
            val promptBiometrics: Boolean = false,
        )
    }
