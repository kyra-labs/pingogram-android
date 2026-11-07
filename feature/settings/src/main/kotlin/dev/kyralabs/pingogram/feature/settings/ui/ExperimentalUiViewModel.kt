package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.repository.ExperimentalUiRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings home's "Experimental UI" (`docs/adr/0035-experimental-ui.md`), off by default ([ExperimentalUiRepository]):
 * null until read, then the setting; a change that cannot be saved is an error. The app follows the saved setting by
 * itself (`MainActivity`), so this only reads and writes it.
 */
@HiltViewModel
class ExperimentalUiViewModel
    @Inject
    constructor(
        private val repository: ExperimentalUiRepository,
    ) : ViewModel() {
        val enabled: StateFlow<Boolean?> =
            repository.enabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        private val errorChannel = Channel<DomainError>(Channel.BUFFERED)
        val errors: Flow<DomainError> = errorChannel.receiveAsFlow()

        fun onCheckedChange(enabled: Boolean) {
            viewModelScope.launch { repository.setEnabled(enabled).onFailure { errorChannel.trySend(it) } }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
