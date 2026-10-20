package dev.kyralabs.pingogram.feature.settings.ui.twostep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.PasswordResetResult
import dev.kyralabs.pingogram.core.domain.model.TwoStepVerificationState
import dev.kyralabs.pingogram.core.domain.repository.TwoStepVerificationRepository
import dev.kyralabs.pingogram.feature.settings.ui.SettingsTimeFormats
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The 2-step verification page (`REQUIREMENTS.md` FR-S1): whether a password is set, with its recovery email, and a
 * recovery email or a reset of the password that waits. The flows that change them are pages of their own
 * ([TwoStepVerificationFlowViewModel]); this page cancels a waiting recovery email or reset, and completes a reset
 * whose waiting period is over.
 *
 * The state loads when the page opens, and again whenever it shows again ([TwoStepVerificationEvent.Refresh]), keeping
 * what shows meanwhile and if that fails: TDLib has no updates for it. Failures of the actions are snackbars.
 */
@HiltViewModel
class TwoStepVerificationViewModel
    @Inject
    constructor(
        private val repository: TwoStepVerificationRepository,
        private val timeFormats: SettingsTimeFormats,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow<TwoStepVerificationUiState>(TwoStepVerificationUiState.Loading)
        val uiState: StateFlow<TwoStepVerificationUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<TwoStepVerificationEffect>(Channel.BUFFERED)
        val effects: Flow<TwoStepVerificationEffect> = effectChannel.receiveAsFlow()

        private var loadJob: Job? = null

        init {
            load(keepContent = false)
        }

        fun onEvent(event: TwoStepVerificationEvent) {
            when (event) {
                TwoStepVerificationEvent.Retry -> {
                    if (uiState.value is TwoStepVerificationUiState.Error) load(keepContent = false)
                }

                TwoStepVerificationEvent.Refresh -> {
                    if (loadJob?.isActive != true && !isWorking()) load(keepContent = true)
                }

                TwoStepVerificationEvent.CancelRecoveryEmail -> {
                    act({ repository.cancelRecoveryEmail() }) { state ->
                        show(state)
                        effectChannel.trySend(TwoStepVerificationEffect.RecoveryEmailCancelled)
                    }
                }

                TwoStepVerificationEvent.CancelReset -> {
                    act({ repository.cancelReset() }) {
                        effectChannel.trySend(TwoStepVerificationEffect.ResetCancelled)
                        load(keepContent = true)
                    }
                }

                TwoStepVerificationEvent.CompleteReset -> {
                    act({ repository.resetPassword() }) { result ->
                        when (result) {
                            PasswordResetResult.Done -> {
                                effectChannel.trySend(TwoStepVerificationEffect.ResetDone)
                            }

                            is PasswordResetResult.Declined -> {
                                val date = timeFormats.formatter().dateTime(result.retryDate)
                                effectChannel.trySend(TwoStepVerificationEffect.ResetDeclined(date))
                            }

                            // Telegram moved the date: the state says when.
                            is PasswordResetResult.Pending -> {
                                Unit
                            }
                        }
                        load(keepContent = true)
                    }
                }
            }
        }

        private fun isWorking() = (uiState.value as? TwoStepVerificationUiState.Content)?.isWorking == true

        /** Loads the state; with [keepContent], what shows stays meanwhile, and if loading fails. */
        private fun load(keepContent: Boolean) {
            loadJob?.cancel()
            if (!keepContent || uiState.value !is TwoStepVerificationUiState.Content) {
                mutableUiState.value = TwoStepVerificationUiState.Loading
            }
            loadJob =
                viewModelScope.launch {
                    when (val loaded = repository.state()) {
                        is Outcome.Success -> {
                            show(loaded.value)
                        }

                        is Outcome.Failure -> {
                            if (uiState.value !is TwoStepVerificationUiState.Content) {
                                mutableUiState.value = TwoStepVerificationUiState.Error(loaded.error)
                            }
                        }
                    }
                }
        }

        /** Runs [action] while the rows wait, then [onSuccess]; a failure is a snackbar. */
        private fun <T> act(
            action: suspend () -> Outcome<T>,
            onSuccess: (T) -> Unit,
        ) {
            val content = uiState.value as? TwoStepVerificationUiState.Content ?: return
            if (content.isWorking) return
            loadJob?.cancel()
            mutableUiState.value = content.copy(isWorking = true)
            viewModelScope.launch {
                val outcome = action()
                mutableUiState.update { (it as? TwoStepVerificationUiState.Content)?.copy(isWorking = false) ?: it }
                when (outcome) {
                    is Outcome.Success -> onSuccess(outcome.value)
                    is Outcome.Failure -> if (outcome.error != DomainError.Silent) showError(outcome.error)
                }
            }
        }

        private fun showError(error: DomainError) {
            effectChannel.trySend(TwoStepVerificationEffect.ShowError(error))
        }

        private fun show(state: TwoStepVerificationState) {
            val resetDate = state.pendingResetDate
            mutableUiState.value =
                TwoStepVerificationUiState.Content(
                    hasPassword = state.hasPassword,
                    hasRecoveryEmail = state.hasRecoveryEmail,
                    pendingEmailPattern = state.pendingRecoveryEmailPattern,
                    pendingReset =
                        resetDate?.let {
                            PendingPasswordReset(
                                date = timeFormats.formatter().dateTime(it),
                                canCompleteNow = timeFormats.now().epochSecond >= it,
                            )
                        },
                )
        }
    }
