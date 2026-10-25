package dev.kyralabs.pingogram.feature.settings.ui.shownfirst

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

@Composable
private fun ChatsShownFirstPreview(state: ChatsShownFirstUiState) {
    PingogramPreview { ChatsShownFirstScreen(state, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun ChatsShownFirstScreenPreview() {
    ChatsShownFirstPreview(ChatsShownFirstPreviewStates.content)
}

@PingogramPreviews
@Composable
private fun ChatsShownFirstScreenEmptyPreview() {
    ChatsShownFirstPreview(ChatsShownFirstPreviewStates.empty)
}

@PingogramPreviews
@Composable
private fun ChatsShownFirstPickerPreview() {
    PingogramPreview { ShownFirstPickerContent(ChatsShownFirstPreviewStates.picker, onEvent = {}) }
}
