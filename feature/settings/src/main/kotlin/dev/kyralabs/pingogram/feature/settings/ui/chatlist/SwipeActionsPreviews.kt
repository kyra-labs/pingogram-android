package dev.kyralabs.pingogram.feature.settings.ui.chatlist

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

@PingogramPreviews
@Composable
private fun SwipeActionsScreenPreview() {
    PingogramPreview {
        SwipeActionsScreen(SwipeActionsPreviewStates.content, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun SwipeActionsScreenChangedPreview() {
    PingogramPreview {
        SwipeActionsScreen(SwipeActionsPreviewStates.changed, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun SwipeActionsScreenChangesFolderPreview() {
    PingogramPreview {
        SwipeActionsScreen(SwipeActionsPreviewStates.changesFolder, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun SwipeActionsScreenLoadingPreview() {
    PingogramPreview {
        SwipeActionsScreen(SwipeActionsPreviewStates.loading, onEvent = {}, onBack = {})
    }
}
