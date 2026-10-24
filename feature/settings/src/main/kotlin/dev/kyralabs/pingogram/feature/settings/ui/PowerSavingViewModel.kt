package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.BatterySaverRepository
import dev.kyralabs.pingogram.core.domain.repository.PowerSavingRepository
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The power saving page: its mode and parts, saved as they change, and whether Battery Saver is on now, followed while
 * the page shows. The repository's settings show what is saved; a change that fails to save leaves them as they were,
 * and a snackbar says why.
 */
@HiltViewModel
class PowerSavingViewModel
    @Inject
    constructor(
        private val repository: PowerSavingRepository,
        batterySaver: BatterySaverRepository,
    ) : ViewModel() {
        private val effectChannel = Channel<PowerSavingEffect>(Channel.BUFFERED)
        val effects: Flow<PowerSavingEffect> = effectChannel.receiveAsFlow()

        val uiState: StateFlow<PowerSavingUiState> =
            combine(repository.settings, batterySaver.isOn) { settings, batterySaverOn ->
                PowerSavingUiState(settings.mode, settings.parts.toImmutableSet(), batterySaverOn)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PowerSavingUiState())

        fun onEvent(event: PowerSavingEvent) {
            viewModelScope.launch {
                val saved =
                    when (event) {
                        is PowerSavingEvent.SetMode -> repository.setMode(event.mode)
                        is PowerSavingEvent.SetPart -> repository.setPart(event.part, event.off)
                    }
                if (saved is Outcome.Failure) effectChannel.trySend(PowerSavingEffect.ShowError(saved.error))
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
