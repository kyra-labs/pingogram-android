package dev.kyralabs.pingogram.feature.profile.chatinfo.actions

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

@PingogramPreviews
@Composable
private fun RecentActionsScreenPreview() {
    PingogramPreview { RecentActionsScreen(RecentActionsPreviewData.content, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun RecentActionsFilteringPreview() {
    PingogramPreview { RecentActionsScreen(RecentActionsPreviewData.filtering, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun RecentActionsPageErrorPreview() {
    PingogramPreview { RecentActionsScreen(RecentActionsPreviewData.pageError, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun RecentActionsEmptyPreview() {
    PingogramPreview { RecentActionsScreen(RecentActionsPreviewData.empty, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun RecentActionsErrorPreview() {
    PingogramPreview { RecentActionsScreen(RecentActionsPreviewData.error, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun RecentActionsLoadingPreview() {
    PingogramPreview { RecentActionsScreen(RecentActionsUiState.Loading, onEvent = {}, onBack = {}) }
}
