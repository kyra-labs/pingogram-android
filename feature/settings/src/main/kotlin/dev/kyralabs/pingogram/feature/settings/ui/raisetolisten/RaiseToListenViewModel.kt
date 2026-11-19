package dev.kyralabs.pingogram.feature.settings.ui.raisetolisten

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.repository.PlaybackSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Raise to listen page shows: nothing until the settings are read, then the settings. */
@Immutable
sealed interface RaiseToListenUiState {
    data object Loading : RaiseToListenUiState

    /** Raise to listen and "Pause when lowered", which only matters while it is on ([pauseWhenLoweredEnabled]). */
    data class Content(
        val settings: RaiseToListenSettings,
    ) : RaiseToListenUiState {
        val pauseWhenLoweredEnabled: Boolean get() = settings.enabled
    }
}

/** What the user changes on the Raise to listen page, handled by [RaiseToListenViewModel.onEvent]. */
sealed interface RaiseToListenEvent {
    /** "Use raise to listen" turned on or off. */
    data class SetEnabled(
        val enabled: Boolean,
    ) : RaiseToListenEvent

    /** "Pause when lowered" turned on or off. */
    data class SetPauseWhenLowered(
        val enabled: Boolean,
    ) : RaiseToListenEvent
}

/** One-off side effects of [RaiseToListenViewModel], handled once by its route. */
sealed interface RaiseToListenEffect {
    /** A setting could not be saved, shown in a snackbar. */
    data class ShowError(
        val error: DomainError,
    ) : RaiseToListenEffect
}

/**
 * The Raise to listen page (owner decision 2026-11-19): the saved settings ([PlaybackSettingsRepository], the keys the
 * Chats page's split row sets too, so the two stay in step), and saving each change; one that cannot be saved does
 * not show, and says so in a snackbar.
 */
@HiltViewModel
class RaiseToListenViewModel
    @Inject
    constructor(
        private val playbackSettingsRepository: PlaybackSettingsRepository,
    ) : ViewModel() {
        val uiState: StateFlow<RaiseToListenUiState> =
            playbackSettingsRepository.raiseToListen
                .map<_, RaiseToListenUiState> { RaiseToListenUiState.Content(it) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, RaiseToListenUiState.Loading)

        private val effectChannel = Channel<RaiseToListenEffect>(Channel.BUFFERED)
        val effects: Flow<RaiseToListenEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: RaiseToListenEvent) {
            viewModelScope.launch {
                val saved =
                    when (event) {
                        is RaiseToListenEvent.SetEnabled -> playbackSettingsRepository.setRaiseToListen(event.enabled)
                        is RaiseToListenEvent.SetPauseWhenLowered -> {
                            playbackSettingsRepository.setPauseWhenLowered(event.enabled)
                        }
                    }
                saved.onFailure { effectChannel.trySend(RaiseToListenEffect.ShowError(it)) }
            }
        }
    }
