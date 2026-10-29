package dev.kyralabs.pingogram.feature.settings.ui.chatlist

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.SwipeAction
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the Swipe actions page shows: nothing until the settings are read, then whether swiping changes the folder and
 * each side's action.
 */
@Immutable
sealed interface SwipeActionsUiState {
    data object Loading : SwipeActionsUiState

    /**
     * What a swipe toward the end ([start], from the start edge) and toward the start ([end]) does, unless
     * [changesFolder], where swiping the list shows the folder tab beside the selected one and rows do not swipe.
     */
    data class Content(
        val start: SwipeAction,
        val end: SwipeAction,
        val changesFolder: Boolean = false,
    ) : SwipeActionsUiState
}

/** The side a swipe moves the row toward: [End] from the start edge, [Start] from the end edge. */
enum class SwipeSide { End, Start }

sealed interface SwipeActionsEvent {
    /** [action] picked for the swipe toward [side]. */
    data class Choose(
        val side: SwipeSide,
        val action: SwipeAction,
    ) : SwipeActionsEvent

    /** "Swipe to change folders" turned on or off. */
    data class SetChangesFolder(
        val changes: Boolean,
    ) : SwipeActionsEvent
}

sealed interface SwipeActionsEffect {
    /** A choice could not be saved: a snackbar, the page keeps what is saved. */
    data class ShowError(
        val error: DomainError,
    ) : SwipeActionsEffect
}

/**
 * Settings → Appearance → Chat list → "Swipe actions" (`docs/audit/feature-gaps-octogram.md`, P2): whether swiping
 * the chat list changes the folder, else what swiping a chat row does toward each side, kept on the device
 * ([ChatListSettingsRepository]) and saved at once. The page shows what is saved, so a choice that cannot be saved
 * changes nothing but a snackbar. Nothing to restore after process death.
 */
@HiltViewModel
class SwipeActionsViewModel
    @Inject
    constructor(
        private val repository: ChatListSettingsRepository,
    ) : ViewModel() {
        val uiState: StateFlow<SwipeActionsUiState> =
            repository.settings
                .map<_, SwipeActionsUiState> {
                    SwipeActionsUiState.Content(it.startSwipe, it.endSwipe, it.swipeChangesFolder)
                }.stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    SwipeActionsUiState.Loading,
                )

        private val effectChannel = Channel<SwipeActionsEffect>(Channel.BUFFERED)
        val effects: Flow<SwipeActionsEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: SwipeActionsEvent) {
            when (event) {
                is SwipeActionsEvent.Choose -> choose(event.side, event.action)
                is SwipeActionsEvent.SetChangesFolder -> setChangesFolder(event.changes)
            }
        }

        private fun setChangesFolder(changes: Boolean) {
            viewModelScope.launch {
                repository
                    .setSwipeChangesFolder(changes)
                    .onFailure { effectChannel.trySend(SwipeActionsEffect.ShowError(it)) }
            }
        }

        private fun choose(
            side: SwipeSide,
            action: SwipeAction,
        ) {
            viewModelScope.launch {
                val saved = repository.settings.first()
                val start = if (side == SwipeSide.End) action else saved.startSwipe
                val end = if (side == SwipeSide.Start) action else saved.endSwipe
                if (start == saved.startSwipe && end == saved.endSwipe) return@launch
                repository
                    .setSwipeActions(start, end)
                    .onFailure { effectChannel.trySend(SwipeActionsEffect.ShowError(it)) }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
