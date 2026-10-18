package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
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
 * The Settings screen: for now, logging out with a confirmation (`REQUIREMENTS.md` FR-A8). Leaving for the login flow
 * is not its job: the app follows TDLib's login state and replaces the main graph once TDLib starts logging out.
 *
 * The open confirmation survives process death through the [SavedStateHandle]; a logout in progress does not, as
 * TDLib reports it by itself after a restart.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val mutableUiState =
            MutableStateFlow(SettingsUiState(isLogOutDialogOpen = savedStateHandle[KEY_LOG_OUT_DIALOG_OPEN] ?: false))
        val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<SettingsEffect>(Channel.BUFFERED)
        val effects: Flow<SettingsEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: SettingsEvent) {
            when (event) {
                SettingsEvent.LogOut -> if (!uiState.value.isLoggingOut) setLogOutDialogOpen(true)
                SettingsEvent.DismissLogOut -> setLogOutDialogOpen(false)
                SettingsEvent.ConfirmLogOut -> logOut()
            }
        }

        /** Sends one logout at a time; a second confirmation while it runs (a double tap) does nothing. */
        private fun logOut() {
            if (uiState.value.isLoggingOut) return
            setLogOutDialogOpen(false)
            mutableUiState.update { it.copy(isLoggingOut = true) }
            viewModelScope.launch {
                // On success the progress stays until the app leaves this screen.
                val error = (authRepository.logOut() as? Outcome.Failure)?.error ?: return@launch
                mutableUiState.update { it.copy(isLoggingOut = false) }
                if (error != DomainError.Silent) effectChannel.trySend(SettingsEffect.ShowError(error))
            }
        }

        private fun setLogOutDialogOpen(open: Boolean) {
            mutableUiState.update { it.copy(isLogOutDialogOpen = open) }
            savedStateHandle[KEY_LOG_OUT_DIALOG_OPEN] = open
        }

        private companion object {
            const val KEY_LOG_OUT_DIALOG_OPEN = "settings.logOutDialogOpen"
        }
    }
