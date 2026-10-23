package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadRules
import dev.kyralabs.pingogram.core.domain.repository.AutoDownloadRepository
import dev.kyralabs.pingogram.core.domain.repository.NetworkRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The automatic download page of one kind of network, [network] (`docs/PERFORMANCE.md` §7): its switches and size
 * limits, saved as they change.
 *
 * A change shows at once and is saved in the background, one after the other, so dragging a size limit through its
 * steps ends at the last one. If saving fails, the page shows what was saved before and a snackbar says why. Data Saver
 * is followed while the page shows, for its note on mobile data and roaming.
 */
@HiltViewModel(assistedFactory = AutoDownloadViewModel.Factory::class)
class AutoDownloadViewModel
    @AssistedInject
    constructor(
        @Assisted private val network: AutoDownloadNetwork,
        private val repository: AutoDownloadRepository,
        networkRepository: NetworkRepository,
    ) : ViewModel() {
        /** Creates the view model of the page of one kind of network. */
        @AssistedFactory
        interface Factory {
            fun create(network: AutoDownloadNetwork): AutoDownloadViewModel
        }

        /** The rules changed on the page and not saved yet, or kept after saving: shown over the saved ones. */
        private val changed = MutableStateFlow<AutoDownloadRules?>(null)

        private val effectChannel = Channel<AutoDownloadEffect>(Channel.BUFFERED)
        val effects: Flow<AutoDownloadEffect> = effectChannel.receiveAsFlow()

        val uiState: StateFlow<AutoDownloadUiState> =
            combine(repository.settings, changed, networkRepository.networkStatus) { settings, changed, status ->
                AutoDownloadUiState(
                    network = network,
                    rules = changed ?: settings.rulesFor(network),
                    isDataSaverOn = status.isDataSaverOn && network != AutoDownloadNetwork.WiFi,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AutoDownloadUiState(network))

        fun onEvent(event: AutoDownloadEvent) {
            when (event) {
                is AutoDownloadEvent.Change -> change(event.rules)
            }
        }

        private fun change(rules: AutoDownloadRules) {
            if (uiState.value.rules == null || rules == changed.value) return
            changed.value = rules
            viewModelScope.launch {
                val saved = repository.setRules(network, rules)
                // A later change replaces this one; only the newest one's failure shows what was saved.
                if (saved is Outcome.Failure && changed.value == rules) {
                    changed.value = null
                    effectChannel.trySend(AutoDownloadEffect.ShowError(saved.error))
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
