package dev.kyralabs.pingogram.feature.settings.ui.twostep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.PasswordResetResult
import dev.kyralabs.pingogram.core.domain.model.TwoStepVerificationState
import dev.kyralabs.pingogram.core.domain.repository.TwoStepVerificationRepository
import dev.kyralabs.pingogram.feature.settings.ui.SettingsTimeFormats
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationFlow.ChangePassword
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationFlow.ChangeRecoveryEmail
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationFlow.ConfirmRecoveryEmail
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationFlow.RemovePassword
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationFlow.SetPassword
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationStep.CurrentPassword
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationStep.EmailCode
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationStep.Hint
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationStep.NewPassword
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationStep.RecoveryCode
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationStep.RecoveryEmail
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationStep.Reset
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * A flow of 2-step verification (`REQUIREMENTS.md` FR-S1), one [TwoStepVerificationFlow] in steps on one page:
 * - **Set password**: the password twice, a hint, a recovery email (or none), then the code sent there; the password
 *   takes effect once the address is confirmed.
 * - **Change password**: the current password, checked at once, then the new one twice and a hint.
 * - **Turn off**: the current password removes it.
 * - **Recovery email**: the current password, the address, then its code; **Confirm recovery email** only the code.
 * - **Forgot password** from the current password's step: a code to the recovery email, then a new password, or,
 *   without a recovery email or access to it, a reset Telegram completes after a waiting period.
 *
 * What was typed lives only here, never in saved state: a process death starts the flow again. Back returns to the step
 * before. Wrong input shows at the field ([TwoStepVerificationFieldError]) with a buzz; `FLOOD_WAIT` counts down while
 * the main action waits; other errors are snackbars. A finished flow leaves with its [TwoStepVerificationResult].
 *
 * It has a handler for each step of five flows, hence `TooManyFunctions` is suppressed.
 */
@Suppress("TooManyFunctions")
@HiltViewModel(assistedFactory = TwoStepVerificationFlowViewModel.Factory::class)
class TwoStepVerificationFlowViewModel
    @AssistedInject
    constructor(
        @Assisted private val flow: TwoStepVerificationFlow,
        private val repository: TwoStepVerificationRepository,
        private val timeFormats: SettingsTimeFormats,
    ) : ViewModel() {
        /** Creates the view model of a flow. */
        @AssistedFactory
        interface Factory {
            fun create(flow: TwoStepVerificationFlow): TwoStepVerificationFlowViewModel
        }

        private val mutableUiState = MutableStateFlow(TwoStepVerificationFlowUiState(flow, firstStep(flow)))
        val uiState: StateFlow<TwoStepVerificationFlowUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<TwoStepVerificationFlowEffect>(Channel.BUFFERED)
        val effects: Flow<TwoStepVerificationFlowEffect> = effectChannel.receiveAsFlow()

        /** The steps before the one that shows, the latest last. */
        private val history = ArrayDeque<TwoStepVerificationStep>()

        private var currentPassword = ""
        private var newPassword = ""
        private var hint = ""
        private var recoveryCode = ""

        private var requestJob: Job? = null
        private var floodWaitJob: Job? = null

        init {
            // The hint of the current password and the address waiting for its code; the flow works without them.
            if (flow != SetPassword) {
                viewModelScope.launch {
                    val state = (repository.state() as? Outcome.Success)?.value ?: return@launch
                    mutableUiState.update {
                        it.copy(
                            passwordHint = state.passwordHint,
                            emailPattern = it.emailPattern ?: state.pendingRecoveryEmailPattern,
                        )
                    }
                }
            }
        }

        fun onEvent(event: TwoStepVerificationFlowEvent) {
            when (event) {
                TwoStepVerificationFlowEvent.InputChanged -> mutableUiState.update { it.copy(fieldError = null) }
                TwoStepVerificationFlowEvent.Back -> back()
                TwoStepVerificationFlowEvent.NoAccessToEmail -> goTo(Reset)
                else -> if (uiState.value.canSubmit) submit(event)
            }
        }

        private fun submit(event: TwoStepVerificationFlowEvent) {
            when (event) {
                is TwoStepVerificationFlowEvent.SubmitCurrentPassword -> submitCurrentPassword(event.password)

                is TwoStepVerificationFlowEvent.SubmitNewPassword -> submitNewPassword(event.password, event.repeated)

                is TwoStepVerificationFlowEvent.SubmitHint -> submitHint(event.hint.trim())

                is TwoStepVerificationFlowEvent.SubmitRecoveryEmail -> submitRecoveryEmail(event.email.trim())

                TwoStepVerificationFlowEvent.SkipRecoveryEmail -> submitRecoveryEmail(null)

                is TwoStepVerificationFlowEvent.SubmitEmailCode -> submitEmailCode(event.code.trim())

                TwoStepVerificationFlowEvent.ResendEmailCode -> resendEmailCode()

                TwoStepVerificationFlowEvent.ForgotPassword -> forgotPassword()

                is TwoStepVerificationFlowEvent.SubmitRecoveryCode -> submitRecoveryCode(event.code.trim())

                TwoStepVerificationFlowEvent.ConfirmReset -> reset()

                TwoStepVerificationFlowEvent.InputChanged,
                TwoStepVerificationFlowEvent.Back,
                TwoStepVerificationFlowEvent.NoAccessToEmail,
                -> Unit
            }
        }

        private fun submitCurrentPassword(password: String) {
            if (password.isEmpty()) return
            if (flow == RemovePassword) {
                request({ repository.removePassword(password) }) { finish(TwoStepVerificationResult.PasswordRemoved) }
            } else {
                request({ repository.checkPassword(password) }) {
                    currentPassword = password
                    goTo(if (flow == ChangeRecoveryEmail) RecoveryEmail else NewPassword)
                }
            }
        }

        private fun submitNewPassword(
            password: String,
            repeated: String,
        ) {
            if (password.isEmpty()) return
            if (password != repeated) {
                reject(TwoStepVerificationFieldError.PasswordsDiffer)
                return
            }
            newPassword = password
            goTo(Hint)
        }

        private fun submitHint(hint: String) {
            if (hint.isNotEmpty() && hint == newPassword) {
                reject(TwoStepVerificationFieldError.HintIsPassword)
                return
            }
            this.hint = hint
            when {
                uiState.value.recovering -> {
                    request({ repository.recoverPassword(recoveryCode, newPassword, hint) }) {
                        finish(TwoStepVerificationResult.PasswordRecovered)
                    }
                }

                flow == SetPassword -> {
                    goTo(RecoveryEmail)
                }

                else -> {
                    request({ repository.setPassword(currentPassword, newPassword, hint) }) {
                        finish(TwoStepVerificationResult.PasswordChanged)
                    }
                }
            }
        }

        private fun submitRecoveryEmail(email: String?) {
            if (email != null && email.isEmpty()) return
            val change: suspend () -> Outcome<TwoStepVerificationState> =
                if (flow == SetPassword) {
                    { repository.setPassword(null, newPassword, hint, email) }
                } else {
                    { repository.setRecoveryEmail(currentPassword, email.orEmpty()) }
                }
            request(change) { state ->
                val pattern = state.pendingRecoveryEmailPattern
                when {
                    pattern != null -> {
                        mutableUiState.update { it.copy(emailPattern = pattern) }
                        goTo(EmailCode)
                    }

                    flow == SetPassword -> {
                        finish(TwoStepVerificationResult.PasswordSet)
                    }

                    else -> {
                        finish(TwoStepVerificationResult.RecoveryEmailConfirmed)
                    }
                }
            }
        }

        private fun submitEmailCode(code: String) {
            if (code.isEmpty()) return
            request({ repository.confirmRecoveryEmail(code) }) {
                finish(
                    if (flow == SetPassword) {
                        TwoStepVerificationResult.PasswordSet
                    } else {
                        TwoStepVerificationResult.RecoveryEmailConfirmed
                    },
                )
            }
        }

        private fun resendEmailCode() {
            request({ repository.resendRecoveryEmailCode() }) { state ->
                mutableUiState.update { it.copy(emailPattern = state.pendingRecoveryEmailPattern ?: it.emailPattern) }
                effectChannel.trySend(TwoStepVerificationFlowEffect.CodeResent)
            }
        }

        private fun forgotPassword() {
            request(
                action = { repository.requestRecovery() },
                onFailure = { error ->
                    if (error == DomainError.PasswordRecoveryUnavailable) {
                        goTo(Reset)
                        true
                    } else {
                        false
                    }
                },
            ) { pattern ->
                mutableUiState.update { it.copy(emailPattern = pattern) }
                goTo(RecoveryCode)
            }
        }

        private fun submitRecoveryCode(code: String) {
            if (code.isEmpty()) return
            request({ repository.checkRecoveryCode(code) }) {
                recoveryCode = code
                mutableUiState.update { it.copy(recovering = true) }
                goTo(NewPassword)
            }
        }

        private fun reset() {
            request({ repository.resetPassword() }) { result ->
                when (result) {
                    PasswordResetResult.Done -> {
                        finish(TwoStepVerificationResult.ResetDone)
                    }

                    is PasswordResetResult.Pending -> {
                        finish(TwoStepVerificationResult.ResetRequested)
                    }

                    is PasswordResetResult.Declined -> {
                        val date = timeFormats.formatter().dateTime(result.retryDate)
                        effectChannel.trySend(TwoStepVerificationFlowEffect.ResetDeclined(date))
                    }
                }
            }
        }

        private fun goTo(step: TwoStepVerificationStep) {
            history.addLast(uiState.value.step)
            mutableUiState.update { it.copy(step = step, fieldError = null, canGoBack = true) }
        }

        private fun back() {
            val previous = history.removeLastOrNull() ?: return
            requestJob?.cancel()
            mutableUiState.update {
                it.copy(
                    step = previous,
                    fieldError = null,
                    isWorking = false,
                    // Back before the recovery code's step leaves the recovery.
                    recovering = it.recovering && previous != RecoveryCode && previous != CurrentPassword,
                    canGoBack = history.isNotEmpty(),
                )
            }
        }

        private fun finish(result: TwoStepVerificationResult) {
            effectChannel.trySend(TwoStepVerificationFlowEffect.Finished(result))
        }

        private fun reject(error: TwoStepVerificationFieldError) {
            mutableUiState.update { it.copy(fieldError = error) }
            effectChannel.trySend(TwoStepVerificationFlowEffect.InputRejected)
        }

        /**
         * Runs [action] while the step waits, then [onSuccess] with its value. A failure goes to [onFailure] first,
         * which returns whether it handled it; otherwise wrong input shows at the field, `FLOOD_WAIT` counts down, and
         * anything else is a snackbar.
         */
        private fun <T> request(
            action: suspend () -> Outcome<T>,
            onFailure: (DomainError) -> Boolean = { false },
            onSuccess: (T) -> Unit,
        ) {
            mutableUiState.update { it.copy(isWorking = true, fieldError = null) }
            requestJob =
                viewModelScope.launch {
                    val outcome = action()
                    mutableUiState.update { it.copy(isWorking = false) }
                    when (outcome) {
                        is Outcome.Success -> onSuccess(outcome.value)
                        is Outcome.Failure -> if (!onFailure(outcome.error)) fail(outcome.error)
                    }
                }
        }

        private fun fail(error: DomainError) {
            when (error) {
                is DomainError.FloodWait -> startFloodWait(error.seconds)
                in FieldErrors -> reject(TwoStepVerificationFieldError.Rejected(error))
                DomainError.Silent -> Unit
                else -> effectChannel.trySend(TwoStepVerificationFlowEffect.ShowError(error))
            }
        }

        private fun startFloodWait(seconds: Long) {
            floodWaitJob?.cancel()
            floodWaitJob =
                viewModelScope.launch {
                    for (left in seconds downTo 1) {
                        mutableUiState.update { it.copy(floodWaitSeconds = left, floodWaitTotalSeconds = seconds) }
                        delay(1.seconds)
                    }
                    mutableUiState.update { it.copy(floodWaitSeconds = null) }
                }
        }

        private companion object {
            /** Errors that concern what was typed into the step's field. */
            val FieldErrors =
                setOf(
                    DomainError.PasswordInvalid,
                    DomainError.CodeInvalid,
                    DomainError.CodeExpired,
                    DomainError.EmailInvalid,
                    DomainError.EmailNotAllowed,
                    DomainError.PasswordRecoveryExpired,
                    DomainError.InputRejected,
                )

            fun firstStep(flow: TwoStepVerificationFlow) =
                when (flow) {
                    SetPassword -> NewPassword
                    ChangePassword, RemovePassword, ChangeRecoveryEmail -> CurrentPassword
                    ConfirmRecoveryEmail -> EmailCode
                }
        }
    }
