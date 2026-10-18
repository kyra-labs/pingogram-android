package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.StorageRepository
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
 * The Data and storage page (`REQUIREMENTS.md` FR-S1): how much space Pingogram takes, loaded when the page opens,
 * and clearing the cache of downloaded files after a confirmation, which shows how much it freed and loads the usage
 * again.
 *
 * The open confirmation survives process death through the [SavedStateHandle]; clearing in progress does not, as
 * TDLib finishes it anyway and the usage is loaded again.
 */
@HiltViewModel
class DataStorageViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val storageRepository: StorageRepository,
    ) : ViewModel() {
        private val mutableUiState =
            MutableStateFlow(
                DataStorageUiState(isClearCacheDialogOpen = savedStateHandle[KEY_CLEAR_CACHE_DIALOG_OPEN] ?: false),
            )
        val uiState: StateFlow<DataStorageUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<DataStorageEffect>(Channel.BUFFERED)
        val effects: Flow<DataStorageEffect> = effectChannel.receiveAsFlow()

        private var loadJob: Job? = null

        init {
            loadUsage()
        }

        fun onEvent(event: DataStorageEvent) {
            when (event) {
                DataStorageEvent.Retry -> {
                    if (uiState.value.usage is StorageUsageState.Error) loadUsage()
                }

                DataStorageEvent.ClearCache -> {
                    if (!uiState.value.isClearingCache) setClearCacheDialogOpen(true)
                }

                DataStorageEvent.DismissClearCache -> {
                    setClearCacheDialogOpen(false)
                }

                DataStorageEvent.ConfirmClearCache -> {
                    clearCache()
                }
            }
        }

        private fun loadUsage() {
            loadJob?.cancel()
            mutableUiState.update { it.copy(usage = StorageUsageState.Loading) }
            loadJob = viewModelScope.launch { mutableUiState.update { it.copy(usage = fetchUsage()) } }
        }

        private suspend fun fetchUsage(): StorageUsageState =
            when (val usage = storageRepository.storageUsage()) {
                is Outcome.Success -> StorageUsageState.Content(usage.value)
                is Outcome.Failure -> StorageUsageState.Error(usage.error)
            }

        /** Clears once at a time; a second confirmation while it runs (a double tap) does nothing. */
        private fun clearCache() {
            if (uiState.value.isClearingCache) return
            setClearCacheDialogOpen(false)
            loadJob?.cancel()
            mutableUiState.update { it.copy(isClearingCache = true) }
            viewModelScope.launch {
                when (val cleared = storageRepository.clearCache()) {
                    is Outcome.Success -> {
                        effectChannel.trySend(DataStorageEffect.CacheCleared(cleared.value))
                        // The figures that were there stay until the new ones arrive, and with them if those fail.
                        val usage = fetchUsage()
                        mutableUiState.update {
                            it.copy(
                                usage = if (usage is StorageUsageState.Error) it.usage else usage,
                                isClearingCache = false,
                            )
                        }
                    }

                    is Outcome.Failure -> {
                        mutableUiState.update { it.copy(isClearingCache = false) }
                        if (cleared.error != DomainError.Silent) {
                            effectChannel.trySend(DataStorageEffect.ShowError(cleared.error))
                        }
                    }
                }
            }
        }

        private fun setClearCacheDialogOpen(open: Boolean) {
            mutableUiState.update { it.copy(isClearCacheDialogOpen = open) }
            savedStateHandle[KEY_CLEAR_CACHE_DIALOG_OPEN] = open
        }

        private companion object {
            const val KEY_CLEAR_CACHE_DIALOG_OPEN = "dataStorage.clearCacheDialogOpen"
        }
    }
