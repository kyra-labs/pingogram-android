package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.BubbleCorners
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Chat appearance page shows: nothing until the settings are read, then the settings. */
@Immutable
sealed interface ChatAppearanceUiState {
    data object Loading : ChatAppearanceUiState

    data class Content(
        val appearance: ChatAppearance,
    ) : ChatAppearanceUiState
}

/** One-off side effects of [ChatAppearanceViewModel], handled once by its route. */
sealed interface ChatAppearanceEffect {
    /** A setting could not be saved, shown in a snackbar (errors are never dialogs). */
    data class ShowError(
        val error: DomainError,
    ) : ChatAppearanceEffect
}

/**
 * The Chat appearance page (`REQUIREMENTS.md` FR-S1): the saved [ChatAppearance], and saving what the user picks. The
 * page shows what is saved, which the theme ([dev.kyralabs.pingogram.core.ui.theme.ChatStyle]) and so the page's own
 * preview follow too: a change shows at once, and one that cannot be saved shows nowhere. Nothing to restore after
 * process death.
 */
@HiltViewModel
class ChatAppearanceViewModel
    @Inject
    constructor(
        private val appearanceRepository: AppearanceRepository,
    ) : ViewModel() {
        val uiState: StateFlow<ChatAppearanceUiState> =
            appearanceRepository.appearance
                .map<_, ChatAppearanceUiState> { ChatAppearanceUiState.Content(it.chat) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, ChatAppearanceUiState.Loading)

        private val effectChannel = Channel<ChatAppearanceEffect>(Channel.BUFFERED)
        val effects: Flow<ChatAppearanceEffect> = effectChannel.receiveAsFlow()

        /** Saves message text's [scale], put on its nearest step. */
        fun setTextScale(scale: Float) =
            save { appearanceRepository.setChatTextScale(ChatAppearance.textScaleOf(scale)) }

        fun setBubbleCorners(corners: BubbleCorners) = save { appearanceRepository.setBubbleCorners(corners) }

        fun setWallpaper(wallpaper: ChatWallpaper) = save { appearanceRepository.setChatWallpaper(wallpaper) }

        private fun save(write: suspend () -> Outcome<Unit>) {
            viewModelScope.launch {
                write().onFailure { effectChannel.trySend(ChatAppearanceEffect.ShowError(it)) }
            }
        }
    }
