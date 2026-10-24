package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.autoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.repository.NetworkRepository
import dev.kyralabs.pingogram.core.domain.repository.NetworkUsageRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Network usage: how much data Pingogram sent and received on each network since the statistics were last reset, as
 * TDLib counts it, read when the page opens and after a reset; it starts on the network the device uses now (Wi-Fi
 * without one), the one chosen kept in [savedStateHandle]. Resetting asks first.
 */
@HiltViewModel
class NetworkUsageViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val repository: NetworkUsageRepository,
        private val networkRepository: NetworkRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow<NetworkUsageUiState>(NetworkUsageUiState.Loading)
        val uiState: StateFlow<NetworkUsageUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<NetworkUsageEffect>(Channel.BUFFERED)
        val effects: Flow<NetworkUsageEffect> = effectChannel.receiveAsFlow()

        init {
            load()
        }

        fun onEvent(event: NetworkUsageEvent) {
            when (event) {
                is NetworkUsageEvent.SelectNetwork -> {
                    savedStateHandle[KEY_NETWORK] = event.network.name
                    updateContent { it.copy(network = event.network) }
                }

                NetworkUsageEvent.Reset -> {
                    updateContent { if (it.isResetting) it else it.copy(isResetDialogOpen = true) }
                }

                NetworkUsageEvent.DismissReset -> {
                    updateContent { it.copy(isResetDialogOpen = false) }
                }

                NetworkUsageEvent.ConfirmReset -> {
                    reset()
                }

                NetworkUsageEvent.Retry -> {
                    if (uiState.value is NetworkUsageUiState.Error) load()
                }
            }
        }

        private fun load() {
            mutableUiState.value = NetworkUsageUiState.Loading
            viewModelScope.launch {
                val network = network()
                mutableUiState.value =
                    when (val outcome = repository.usage()) {
                        is Outcome.Success -> NetworkUsageUiState.Content(outcome.value, network)
                        is Outcome.Failure -> NetworkUsageUiState.Error(outcome.error)
                    }
            }
        }

        /** The network chosen before, or the one the device uses now, or Wi-Fi. */
        private suspend fun network(): AutoDownloadNetwork {
            val saved = savedStateHandle.get<String>(KEY_NETWORK)
            AutoDownloadNetwork.entries.firstOrNull { it.name == saved }?.let { return it }
            return networkRepository.networkStatus
                .first()
                .type.autoDownloadNetwork ?: AutoDownloadNetwork.WiFi
        }

        private fun reset() {
            val content = uiState.value as? NetworkUsageUiState.Content ?: return
            if (content.isResetting) return
            mutableUiState.value = content.copy(isResetDialogOpen = false, isResetting = true)
            viewModelScope.launch {
                val error = (repository.reset() as? Outcome.Failure)?.error
                if (error == null) {
                    effectChannel.trySend(NetworkUsageEffect.WasReset)
                    val usage = (repository.usage() as? Outcome.Success)?.value
                    updateContent { it.copy(usage = usage ?: it.usage, isResetting = false) }
                } else {
                    updateContent { it.copy(isResetting = false) }
                    if (error != DomainError.Silent) effectChannel.trySend(NetworkUsageEffect.ShowError(error))
                }
            }
        }

        private fun updateContent(transform: (NetworkUsageUiState.Content) -> NetworkUsageUiState.Content) =
            mutableUiState.update { if (it is NetworkUsageUiState.Content) transform(it) else it }

        internal companion object {
            const val KEY_NETWORK = "networkUsage.network"
        }
    }
