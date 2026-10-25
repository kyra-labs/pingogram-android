package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.DataOptionsRepository
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
 * Data and storage's "Prefer IPv6" and "Delete cloud drafts" (`docs/audit/feature-gaps-octogram.md` P3,
 * [DataOptionsRepository]). A switched preference shows at once and flips back with an error when Telegram's library
 * refuses it; deleting the drafts asks first, the question kept through process death in the [SavedStateHandle].
 */
@HiltViewModel
class DataOptionsViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val repository: DataOptionsRepository,
    ) : ViewModel() {
        private val mutableUiState =
            MutableStateFlow(
                DataOptionsUiState(isDeleteDraftsDialogOpen = savedStateHandle[KEY_DELETE_DRAFTS_DIALOG] ?: false),
            )
        val uiState: StateFlow<DataOptionsUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<DataOptionsEffect>(Channel.BUFFERED)
        val effects: Flow<DataOptionsEffect> = effectChannel.receiveAsFlow()

        init {
            viewModelScope.launch {
                repository.prefersIpv6.collect { prefers -> mutableUiState.update { it.copy(prefersIpv6 = prefers) } }
            }
        }

        fun onEvent(event: DataOptionsEvent) {
            when (event) {
                is DataOptionsEvent.SetPrefersIpv6 -> setPrefersIpv6(event.prefers)
                DataOptionsEvent.DeleteDrafts -> if (!uiState.value.isDeletingDrafts) setDialogOpen(true)
                DataOptionsEvent.DismissDeleteDrafts -> setDialogOpen(false)
                DataOptionsEvent.ConfirmDeleteDrafts -> deleteDrafts()
            }
        }

        private fun setPrefersIpv6(prefers: Boolean) {
            val previous = uiState.value.prefersIpv6 ?: return
            mutableUiState.update { it.copy(prefersIpv6 = prefers) }
            viewModelScope.launch {
                val saved = repository.setPrefersIpv6(prefers)
                if (saved is Outcome.Failure) {
                    mutableUiState.update { it.copy(prefersIpv6 = previous) }
                    showError(saved.error)
                }
            }
        }

        /** Deletes once at a time; a second confirmation while it runs (a double tap) does nothing. */
        private fun deleteDrafts() {
            setDialogOpen(false)
            if (uiState.value.isDeletingDrafts) return
            mutableUiState.update { it.copy(isDeletingDrafts = true) }
            viewModelScope.launch {
                val deleted = repository.deleteCloudDrafts()
                mutableUiState.update { it.copy(isDeletingDrafts = false) }
                when (deleted) {
                    is Outcome.Success -> effectChannel.trySend(DataOptionsEffect.DraftsDeleted)
                    is Outcome.Failure -> showError(deleted.error)
                }
            }
        }

        private fun setDialogOpen(open: Boolean) {
            mutableUiState.update { it.copy(isDeleteDraftsDialogOpen = open) }
            savedStateHandle[KEY_DELETE_DRAFTS_DIALOG] = open
        }

        private fun showError(error: DomainError) {
            if (error != DomainError.Silent) effectChannel.trySend(DataOptionsEffect.ShowError(error))
        }

        private companion object {
            const val KEY_DELETE_DRAFTS_DIALOG = "dataOptions.deleteDraftsDialogOpen"
        }
    }

/**
 * What [DataOptionsViewModel] shows: whether connections prefer IPv6 (null until read), whether the question before
 * deleting the cloud drafts is open, and whether they are being deleted.
 */
@Immutable
data class DataOptionsUiState(
    val prefersIpv6: Boolean? = null,
    val isDeleteDraftsDialogOpen: Boolean = false,
    val isDeletingDrafts: Boolean = false,
)

/** What the user does with Data and storage's options, handled by [DataOptionsViewModel.onEvent]. */
sealed interface DataOptionsEvent {
    data class SetPrefersIpv6(
        val prefers: Boolean,
    ) : DataOptionsEvent

    /** "Delete cloud drafts" was tapped: ask first. */
    data object DeleteDrafts : DataOptionsEvent

    data object ConfirmDeleteDrafts : DataOptionsEvent

    data object DismissDeleteDrafts : DataOptionsEvent
}

/** What [DataOptionsViewModel] does once, shown in snackbars. */
sealed interface DataOptionsEffect {
    data object DraftsDeleted : DataOptionsEffect

    data class ShowError(
        val error: DomainError,
    ) : DataOptionsEffect
}
