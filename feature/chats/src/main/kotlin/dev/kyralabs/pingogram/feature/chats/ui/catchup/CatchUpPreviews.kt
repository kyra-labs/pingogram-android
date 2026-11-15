package dev.kyralabs.pingogram.feature.chats.ui.catchup

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

// Catch-up's states (CatchUpScreen), each in the previews' themes and sizes.

@PingogramPreviews
@Composable
private fun CatchUpCardsPreview() {
    PingogramPreview { CatchUpScreen(CatchUpPreviewData.cards, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun CatchUpReplyingPreview() {
    PingogramPreview { CatchUpScreen(CatchUpPreviewData.replying, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun CatchUpDonePreview() {
    PingogramPreview { CatchUpScreen(CatchUpPreviewData.done, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun CatchUpEmptyPreview() {
    PingogramPreview { CatchUpScreen(CatchUpUiState(CatchUpStage.Empty), onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun CatchUpLoadingPreview() {
    PingogramPreview { CatchUpScreen(CatchUpUiState(), onEvent = {}, onBack = {}) }
}
