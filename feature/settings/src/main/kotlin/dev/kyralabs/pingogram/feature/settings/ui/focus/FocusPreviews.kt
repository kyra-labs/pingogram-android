package dev.kyralabs.pingogram.feature.settings.ui.focus

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

@PingogramPreviews
@Composable
private fun FocusScreenPreview() {
    PingogramPreview { FocusScreen(FocusPreviewStates.content, onEvent = {}, onOpenProfile = { _, _ -> }, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun FocusScreenEmptyPreview() {
    PingogramPreview { FocusScreen(FocusPreviewStates.empty, onEvent = {}, onOpenProfile = { _, _ -> }, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun FocusScreenLoadingPreview() {
    PingogramPreview { FocusScreen(FocusUiState(), onEvent = {}, onOpenProfile = { _, _ -> }, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun FocusEditScreenPreview() {
    PingogramPreview { FocusEditScreen(FocusPreviewStates.editing, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun FocusEditScreenNewPreview() {
    PingogramPreview { FocusEditScreen(FocusPreviewStates.newByHand, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun FocusEditScreenDeletePreview() {
    PingogramPreview { FocusEditScreen(FocusPreviewStates.askDelete, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun FocusEditScreenLoadingPreview() {
    PingogramPreview { FocusEditScreen(FocusEditUiState(), onEvent = {}, onBack = {}) }
}
