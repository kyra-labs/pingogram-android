package dev.kyralabs.pingogram.feature.settings.ui.passcode

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Setting a passcode: choosing it, then entering it again. */
enum class SetPasscodeStep { Choose, Confirm }

/**
 * What the Set passcode page shows.
 *
 * @property step choosing the passcode, or entering it again.
 * @property entry the passcode entry; while saving, it is checking.
 */
@Immutable
data class SetPasscodeUiState(
    val step: SetPasscodeStep = SetPasscodeStep.Choose,
    val entry: PasscodeEntryUiState = PasscodeEntryUiState(),
)

/** What the user does on the Set passcode page, handled by [SetPasscodeViewModel.onEvent]. */
sealed interface SetPasscodeEvent {
    data object Submit : SetPasscodeEvent

    /** Switches between a PIN and a password, while choosing. */
    data object SwitchType : SetPasscodeEvent

    /** Back from entering it again to choosing it. */
    data object Back : SetPasscodeEvent
}

/** One-off side effects of [SetPasscodeViewModel], handled once by the page. */
sealed interface SetPasscodeEffect {
    /** The passcode is set: the page closes. */
    data object Saved : SetPasscodeEffect
}

/**
 * The Set passcode page (`REQUIREMENTS.md` FR-S1): a PIN of 4 to 8 digits or a password of 4 characters and more
 * ([PasscodeType.accepts]), entered twice, then set ([PasscodeRepository.setPasscode]), replacing any passcode set
 * before. A second entry that differs starts over. The first entry stays in memory only, so after process death the
 * page starts over too.
 */
@HiltViewModel
class SetPasscodeViewModel
    @Inject
    constructor(
        private val passcodeRepository: PasscodeRepository,
    ) : ViewModel() {
        /** The passcode being entered. */
        val input = PasscodeInput()

        private val mutableUiState = MutableStateFlow(SetPasscodeUiState())
        val uiState: StateFlow<SetPasscodeUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<SetPasscodeEffect>(Channel.BUFFERED)
        val effects: Flow<SetPasscodeEffect> = effectChannel.receiveAsFlow()

        private var chosen: String? = null

        fun onEvent(event: SetPasscodeEvent) {
            when (event) {
                SetPasscodeEvent.Submit -> submit()
                SetPasscodeEvent.SwitchType -> switchType()
                SetPasscodeEvent.Back -> back()
            }
        }

        private fun submit() {
            val state = uiState.value
            val passcode = input.text
            if (!state.entry.canSubmit) return
            when (state.step) {
                SetPasscodeStep.Choose -> {
                    input.clear()
                    if (state.entry.type.accepts(passcode)) {
                        chosen = passcode
                        mutableUiState.update {
                            it.copy(
                                step = SetPasscodeStep.Confirm,
                                entry = it.entry.copy(message = null),
                            )
                        }
                    } else {
                        reject(PasscodeMessage.Invalid)
                    }
                }

                SetPasscodeStep.Confirm -> {
                    if (passcode == chosen) save(passcode, state.entry.type) else startOver(PasscodeMessage.Mismatch)
                }
            }
        }

        private fun save(
            passcode: String,
            type: PasscodeType,
        ) {
            mutableUiState.update { it.copy(entry = it.entry.copy(isChecking = true, message = null)) }
            viewModelScope.launch {
                val result = passcodeRepository.setPasscode(passcode, type)
                input.clear()
                when (result) {
                    is Outcome.Success -> {
                        chosen = null
                        mutableUiState.update { it.copy(entry = it.entry.copy(isChecking = false)) }
                        effectChannel.trySend(SetPasscodeEffect.Saved)
                    }

                    is Outcome.Failure -> {
                        startOver(PasscodeMessage.Failed(result.error))
                    }
                }
            }
        }

        private fun switchType() {
            if (uiState.value.step != SetPasscodeStep.Choose) return
            input.clear()
            mutableUiState.update { state ->
                val type = if (state.entry.type == PasscodeType.Pin) PasscodeType.Password else PasscodeType.Pin
                state.copy(entry = state.entry.copy(type = type, message = null))
            }
        }

        private fun back() {
            if (uiState.value.step == SetPasscodeStep.Confirm) startOver(message = null)
        }

        /** Back to choosing, with [message] (counting as a rejection) or none. */
        private fun startOver(message: PasscodeMessage?) {
            chosen = null
            input.clear()
            mutableUiState.update { state ->
                state.copy(
                    step = SetPasscodeStep.Choose,
                    entry =
                        state.entry.copy(
                            message = message,
                            isChecking = false,
                            rejections = if (message != null) state.entry.rejections + 1 else state.entry.rejections,
                        ),
                )
            }
        }

        private fun reject(message: PasscodeMessage) {
            mutableUiState.update {
                it.copy(entry = it.entry.copy(message = message, rejections = it.entry.rejections + 1))
            }
        }
    }
