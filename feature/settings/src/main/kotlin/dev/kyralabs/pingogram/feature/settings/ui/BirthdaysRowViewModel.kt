package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.repository.BirthdaysRowRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Privacy and security's "Birthdays" (`REQUIREMENTS.md` FR-X18), whether the Contacts tab shows its Birthdays row, on
 * by default ([BirthdaysRowRepository]): null until read, then the setting; a change that cannot be saved is an error.
 */
@HiltViewModel
class BirthdaysRowViewModel
    @Inject
    constructor(
        private val repository: BirthdaysRowRepository,
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
