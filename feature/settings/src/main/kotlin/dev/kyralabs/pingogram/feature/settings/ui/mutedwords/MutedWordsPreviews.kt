package dev.kyralabs.pingogram.feature.settings.ui.mutedwords

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

@PingogramPreviews
@Composable
private fun MutedWordsScreenPreview() {
    PingogramPreview { MutedWordsScreen(MutedWordsPreviewStates.words, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun MutedWordsScreenEmptyPreview() {
    PingogramPreview { MutedWordsScreen(MutedWordsPreviewStates.empty, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun MutedWordsScreenFullPreview() {
    PingogramPreview { MutedWordsScreen(MutedWordsPreviewStates.full, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun MutedWordsScreenLoadingPreview() {
    PingogramPreview { MutedWordsScreen(MutedWordsUiState(), onEvent = {}, onBack = {}) }
}
