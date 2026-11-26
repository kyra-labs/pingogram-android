package dev.kyralabs.pingogram.feature.settings.ui.mutedwords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.MutedWordsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Muted words' page (`REQUIREMENTS.md` FR-X21): the switch and the words, kept on this phone ([MutedWordsRepository]).
 * Words can be added while it is off. Each change is saved at once; one that fails is a snackbar.
 */
@HiltViewModel
class MutedWordsViewModel
    @Inject
    constructor(
        private val repository: MutedWordsRepository,
    ) : ViewModel() {
        private val effectChannel = Channel<MutedWordsEffect>(Channel.BUFFERED)
        val effects: Flow<MutedWordsEffect> = effectChannel.receiveAsFlow()

        val uiState: StateFlow<MutedWordsUiState> =
            repository.settings
                .map(::MutedWordsUiState)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MutedWordsUiState())

        fun onEvent(event: MutedWordsEvent) {
            when (event) {
                is MutedWordsEvent.SetEnabled -> {
                    save { repository.setEnabled(event.enabled) }
                }

                is MutedWordsEvent.Add -> {
                    if (event.word.isNotBlank()) save(added = true) { repository.addWord(event.word) }
                }

                is MutedWordsEvent.Remove -> {
                    save { repository.removeWord(event.word) }
                }
            }
        }

        private fun save(
            added: Boolean = false,
            change: suspend () -> Outcome<Unit>,
        ) {
            viewModelScope.launch {
                when (val outcome = change()) {
                    is Outcome.Success -> if (added) effectChannel.trySend(MutedWordsEffect.Added)
                    is Outcome.Failure -> effectChannel.trySend(MutedWordsEffect.ShowError(outcome.error))
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
