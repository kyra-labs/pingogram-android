package dev.kyralabs.pingogram.feature.settings.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.repository.DailyDigestSettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Daily digest's settings on the Notifications page (`REQUIREMENTS.md` FR-X23): whether it comes and around what
 * time. It shows what is saved, so a change that cannot be saved does not show, and says so in a snackbar.
 */
@HiltViewModel
class DailyDigestViewModel
    @Inject
    constructor(
        private val repository: DailyDigestSettingsRepository,
    ) : ViewModel() {
        private val pickingTime = MutableStateFlow(false)

        val uiState: StateFlow<DailyDigestUiState> =
            combine(repository.settings, pickingTime, ::DailyDigestUiState)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DailyDigestUiState())

        private val effectChannel = Channel<DailyDigestEffect>(Channel.BUFFERED)
        val effects: Flow<DailyDigestEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: DailyDigestEvent) {
            when (event) {
                is DailyDigestEvent.SetEnabled -> {
                    save { repository.setEnabled(event.enabled) }
                }

                DailyDigestEvent.PickTime -> {
                    pickingTime.value = true
                }

                DailyDigestEvent.DismissTime -> {
                    pickingTime.value = false
                }

                is DailyDigestEvent.SetTime -> {
                    pickingTime.value = false
                    save { repository.setTime(event.time) }
                }
            }
        }

        private fun save(change: suspend () -> Outcome<Unit>) {
            viewModelScope.launch { change().onFailure { effectChannel.trySend(DailyDigestEffect.ShowError(it)) } }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
