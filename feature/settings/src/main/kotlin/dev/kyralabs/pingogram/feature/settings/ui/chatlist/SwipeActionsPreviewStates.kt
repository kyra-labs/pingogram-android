package dev.kyralabs.pingogram.feature.settings.ui.chatlist

import dev.kyralabs.pingogram.core.domain.repository.SwipeAction

/** The states of the Swipe actions page, shared by previews and screenshot tests. */
internal object SwipeActionsPreviewStates {
    val content = SwipeActionsUiState.Content(start = SwipeAction.Read, end = SwipeAction.Archive)

    val changed = SwipeActionsUiState.Content(start = SwipeAction.Pin, end = SwipeAction.None)

    val loading = SwipeActionsUiState.Loading
}
