package dev.kyralabs.pingogram.feature.settings.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.AccountRepository
import dev.kyralabs.pingogram.core.domain.repository.TwoStepVerificationRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * The Delete account page (Google Play's account deletion policy, `docs/PLAY_STORE.md`): how long the account may stay
 * unused before Telegram deletes it, and deleting it now.
 *
 * It loads that period and whether 2-step verification is on at once; either failing shows why, with "Try again". A
 * period chosen shows at once and goes to Telegram; refused, the previous one returns with a snackbar. Deleting asks
 * first ([DeleteAccountUiState.Content.confirming]), then sends the reason and the password: a wrong password shows
 * under its field with a buzz, a `FLOOD_WAIT` counts down, anything else is a snackbar. Once Telegram deleted the
 * account the page stays as it is, "deleting", while TDLib logs out and the app returns to its welcome screen. What is
 * typed stays in the page, never in this view model or its saved state.
 */
@HiltViewModel
class DeleteAccountViewModel
    @Inject
    constructor(
        private val accountRepository: AccountRepository,
        private val twoStepVerificationRepository: TwoStepVerificationRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow<DeleteAccountUiState>(DeleteAccountUiState.Loading)
        val uiState: StateFlow<DeleteAccountUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<DeleteAccountEffect>(Channel.BUFFERED)
        val effects: Flow<DeleteAccountEffect> = effectChannel.receiveAsFlow()

        private var ttlJob: Job? = null
        private var floodWaitJob: Job? = null

        init {
            load()
        }

        fun onEvent(event: DeleteAccountEvent) {
            when (event) {
                DeleteAccountEvent.Retry -> {
                    if (uiState.value is DeleteAccountUiState.Error) load()
                }

                is DeleteAccountEvent.SelectTtl -> {
                    selectTtl(event.days)
                }

                DeleteAccountEvent.RequestDeletion -> {
                    updateContent {
                        if (it.isDeleting) {
                            it
                        } else {
                            it.copy(
                                confirming = true,
                            )
                        }
                    }
                }

                DeleteAccountEvent.DismissConfirmation -> {
                    updateContent { it.copy(confirming = false) }
                }

                is DeleteAccountEvent.ConfirmDeletion -> {
                    delete(event.reason, event.password)
                }

                DeleteAccountEvent.PasswordChanged -> {
                    updateContent { it.copy(passwordError = null) }
                }
            }
        }

        private fun load() {
            mutableUiState.value = DeleteAccountUiState.Loading
            viewModelScope.launch {
                val ttl = async { accountRepository.accountTtl() }
                val password = async { twoStepVerificationRepository.state() }
                val loadedTtl = ttl.await()
                val loadedPassword = password.await()
                mutableUiState.value =
                    when {
                        loadedTtl is Outcome.Failure -> {
                            DeleteAccountUiState.Error(loadedTtl.error)
                        }

                        loadedPassword is Outcome.Failure -> {
                            DeleteAccountUiState.Error(loadedPassword.error)
                        }

                        else -> {
                            val days = (loadedTtl as Outcome.Success).value
                            val state = (loadedPassword as Outcome.Success).value
                            DeleteAccountUiState.Content(
                                ttlDays = days,
                                ttlOptions = ttlOptions(days),
                                hasPassword = state.hasPassword,
                                passwordHint = state.passwordHint,
                            )
                        }
                    }
            }
        }

        private fun selectTtl(days: Int) {
            val content = uiState.value as? DeleteAccountUiState.Content ?: return
            if (days == content.ttlDays || content.savingTtl) return
            val previous = content.ttlDays
            updateContent { it.copy(ttlDays = days, savingTtl = true) }
            ttlJob =
                viewModelScope.launch {
                    val outcome = accountRepository.setAccountTtl(days)
                    updateContent {
                        if (outcome is Outcome.Failure) {
                            it.copy(ttlDays = previous, savingTtl = false)
                        } else {
                            it.copy(savingTtl = false)
                        }
                    }
                    if (outcome is Outcome.Failure) effectChannel.trySend(DeleteAccountEffect.ShowError(outcome.error))
                }
        }

        private fun delete(
            reason: String,
            password: String,
        ) {
            val content = uiState.value as? DeleteAccountUiState.Content ?: return
            if (!content.canDelete(passwordEntered = password.isNotEmpty())) {
                updateContent { it.copy(confirming = false) }
                return
            }
            updateContent { it.copy(confirming = false, isDeleting = true, passwordError = null) }
            viewModelScope.launch {
                when (val outcome = accountRepository.deleteAccount(reason.trim(), password)) {
                    is Outcome.Success -> {
                        effectChannel.trySend(DeleteAccountEffect.Deleted)
                    }

                    is Outcome.Failure -> {
                        updateContent { it.copy(isDeleting = false) }
                        fail(outcome.error)
                    }
                }
            }
        }

        private fun fail(error: DomainError) {
            when (error) {
                is DomainError.FloodWait -> {
                    startFloodWait(error.seconds)
                }

                DomainError.PasswordInvalid -> {
                    updateContent { it.copy(passwordError = error) }
                    effectChannel.trySend(DeleteAccountEffect.PasswordRejected)
                }

                DomainError.Silent -> {
                    Unit
                }

                else -> {
                    effectChannel.trySend(DeleteAccountEffect.ShowError(error))
                }
            }
        }

        private fun startFloodWait(seconds: Long) {
            floodWaitJob?.cancel()
            floodWaitJob =
                viewModelScope.launch {
                    for (left in seconds downTo 1) {
                        updateContent { it.copy(floodWaitSeconds = left, floodWaitTotalSeconds = seconds) }
                        delay(1.seconds)
                    }
                    updateContent { it.copy(floodWaitSeconds = null) }
                }
        }

        private inline fun updateContent(
            crossinline change: (DeleteAccountUiState.Content) -> DeleteAccountUiState.Content,
        ) {
            mutableUiState.update { if (it is DeleteAccountUiState.Content) change(it) else it }
        }

        internal companion object {
            /** 1, 3, 6, 12, 18 and 24 months, as Telegram's apps offer them; Telegram takes 30 to 730 days. */
            val TtlChoices = listOf(30, 90, 180, 365, 548, 730)

            /** The choices, with [current] among them where another app set a period of its own. */
            fun ttlOptions(current: Int): ImmutableList<Int> =
                (TtlChoices + current).distinct().sorted().toImmutableList()
        }
    }
