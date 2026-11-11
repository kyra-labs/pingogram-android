package dev.kyralabs.pingogram.feature.settings.ui.passkeys

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Passkey
import dev.kyralabs.pingogram.core.domain.model.PasskeyRegistration
import dev.kyralabs.pingogram.core.domain.repository.PasskeyRepository
import dev.kyralabs.pingogram.core.domain.repository.SessionRepository
import dev.kyralabs.pingogram.core.domain.repository.TwoStepVerificationRepository
import dev.kyralabs.pingogram.core.ui.passkey.PasskeyResult
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsTimeFormats
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
 * Privacy and security → Passkeys (`docs/adr/0037-passkeys.md`): the account's passkeys, loaded when the page opens
 * and again whenever it shows, with whether 2-step verification is on and how many sessions there are. Adding one asks
 * Telegram for its request, hands it to the password managers through the route ([PasskeysEffect.CreatePasskey]) and
 * gives Telegram their answer; removing one asks first, in a confirmation that survives process death.
 */
@HiltViewModel
class PasskeysViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val repository: PasskeyRepository,
        private val twoStepVerificationRepository: TwoStepVerificationRepository,
        private val sessionRepository: SessionRepository,
        private val timeFormats: SettingsTimeFormats,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(PasskeysUiState(dialog = savedStateHandle.restoredDialog()))
        val uiState: StateFlow<PasskeysUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<PasskeysEffect>(Channel.BUFFERED)
        val effects: Flow<PasskeysEffect> = effectChannel.receiveAsFlow()

        private var loadJob: Job? = null

        init {
            load(keepContent = false)
        }

        fun onEvent(event: PasskeysEvent) {
            when (event) {
                PasskeysEvent.Refresh -> {
                    if (loadJob?.isActive != true) load(keepContent = true)
                }

                PasskeysEvent.Retry -> {
                    if (uiState.value.passkeys is PasskeysState.Failed) load(keepContent = false)
                }

                PasskeysEvent.Add -> {
                    add()
                }

                is PasskeysEvent.Created -> {
                    created(event.result)
                }

                is PasskeysEvent.Remove -> {
                    if (event.passkey.id !in uiState.value.removingIds) {
                        setDialog(RemovePasskeyDialog(event.passkey.id, event.passkey.name))
                    }
                }

                PasskeysEvent.Confirm -> {
                    uiState.value.dialog?.let(::remove)
                    setDialog(null)
                }

                PasskeysEvent.Dismiss -> {
                    setDialog(null)
                }
            }
        }

        /** Loads the passkeys and what else protects the account; with [keepContent], what shows stays meanwhile. */
        private fun load(keepContent: Boolean) {
            loadJob?.cancel()
            if (!keepContent || uiState.value.passkeys !is PasskeysState.Loaded) {
                mutableUiState.update { it.copy(passkeys = PasskeysState.Loading) }
            }
            loadJob =
                viewModelScope.launch {
                    val twoStep = async { twoStepVerificationRepository.state() }
                    val sessions = async { sessionRepository.activeSessions() }
                    val passkeys = repository.passkeys()
                    val protection =
                        AccountProtection(
                            twoStepVerification = (twoStep.await() as? Outcome.Success)?.value?.hasPassword,
                            sessionCount = (sessions.await() as? Outcome.Success)?.value?.sessions?.size,
                        )
                    mutableUiState.update { state ->
                        val shown =
                            when (passkeys) {
                                is Outcome.Success -> {
                                    PasskeysState.Loaded(
                                        passkeys.value.map { it.toItem(timeFormats) }.toImmutableList(),
                                    )
                                }

                                is Outcome.Failure -> {
                                    state.passkeys as? PasskeysState.Loaded ?: PasskeysState.Failed(passkeys.error)
                                }
                            }
                        state.copy(
                            passkeys = shown,
                            protection =
                                AccountProtection(
                                    twoStepVerification =
                                        protection.twoStepVerification ?: state.protection.twoStepVerification,
                                    sessionCount = protection.sessionCount ?: state.protection.sessionCount,
                                ),
                        )
                    }
                }
        }

        private fun add() {
            if (uiState.value.isAdding) return
            mutableUiState.update { it.copy(isAdding = true) }
            viewModelScope.launch {
                when (val options = repository.creationOptions()) {
                    is Outcome.Success -> {
                        effectChannel.trySend(PasskeysEffect.CreatePasskey(options.value))
                    }

                    is Outcome.Failure -> {
                        mutableUiState.update { it.copy(isAdding = false) }
                        showError(options.error)
                    }
                }
            }
        }

        private fun created(result: PasskeyResult<PasskeyRegistration>) {
            when (result) {
                is PasskeyResult.Done -> {
                    register(result.value)
                }

                PasskeyResult.Cancelled -> {
                    mutableUiState.update { it.copy(isAdding = false) }
                }

                PasskeyResult.Unavailable -> {
                    mutableUiState.update { it.copy(isAdding = false) }
                    effectChannel.trySend(PasskeysEffect.Unavailable)
                }

                PasskeyResult.Failed, PasskeyResult.NotFound -> {
                    mutableUiState.update { it.copy(isAdding = false) }
                    effectChannel.trySend(PasskeysEffect.Failed)
                }
            }
        }

        private fun register(registration: PasskeyRegistration) {
            viewModelScope.launch {
                val added = repository.add(registration)
                mutableUiState.update { it.copy(isAdding = false) }
                when (added) {
                    is Outcome.Success -> {
                        val item = added.value.toItem(timeFormats)
                        updateLoaded { passkeys -> passkeys.filterNot { it.id == item.id } + item }
                        effectChannel.trySend(PasskeysEffect.Added(item.name))
                    }

                    is Outcome.Failure -> {
                        showError(added.error)
                    }
                }
            }
        }

        private fun remove(dialog: RemovePasskeyDialog) {
            if (dialog.id in uiState.value.removingIds) return
            setRemoving(dialog.id, removing = true)
            viewModelScope.launch {
                val removed = repository.remove(dialog.id)
                setRemoving(dialog.id, removing = false)
                when (removed) {
                    is Outcome.Success -> {
                        updateLoaded { passkeys -> passkeys.filterNot { it.id == dialog.id } }
                        effectChannel.trySend(PasskeysEffect.Removed(dialog.name))
                    }

                    is Outcome.Failure -> {
                        showError(removed.error)
                    }
                }
            }
        }

        private fun showError(error: DomainError) {
            if (error != DomainError.Silent) effectChannel.trySend(PasskeysEffect.ShowError(error))
        }

        private fun setRemoving(
            id: String,
            removing: Boolean,
        ) = mutableUiState.update {
            val ids = if (removing) it.removingIds + id else it.removingIds - id
            it.copy(removingIds = ids.toPersistentSet())
        }

        private fun updateLoaded(transform: (List<PasskeyItem>) -> List<PasskeyItem>) =
            mutableUiState.update { state ->
                val loaded = state.passkeys as? PasskeysState.Loaded
                if (loaded == null) {
                    state
                } else {
                    state.copy(passkeys = PasskeysState.Loaded(transform(loaded.passkeys).toImmutableList()))
                }
            }

        private fun setDialog(dialog: RemovePasskeyDialog?) {
            mutableUiState.update { it.copy(dialog = dialog) }
            savedStateHandle[KEY_DIALOG_ID] = dialog?.id
            savedStateHandle[KEY_DIALOG_NAME] = dialog?.name
        }
    }

/** This passkey as its row shows it, "Added Oct 14 · used 9:41 AM" in the device's time settings ([timeFormats]). */
private fun Passkey.toItem(timeFormats: SettingsTimeFormats): PasskeyItem {
    val formatter = timeFormats.formatter()
    val now = timeFormats.now()
    val added = UiText.resource(R.string.settings_passkey_added_on, formatter.lastActive(addedAt, now))
    val used =
        if (lastUsedAt > 0) {
            UiText.resource(R.string.settings_passkey_used_on, formatter.lastActive(lastUsedAt, now))
        } else {
            UiText.resource(R.string.settings_passkey_never_used)
        }
    return PasskeyItem(id = id, name = name, details = UiText.resource(R.string.settings_passkey_dates, added, used))
}

/** The removal the view model was confirming before the process died; null if none was. */
private fun SavedStateHandle.restoredDialog(): RemovePasskeyDialog? =
    get<String>(KEY_DIALOG_ID)?.let { RemovePasskeyDialog(it, get<String>(KEY_DIALOG_NAME).orEmpty()) }

private const val KEY_DIALOG_ID = "passkeys.dialogId"
private const val KEY_DIALOG_NAME = "passkeys.dialogName"
