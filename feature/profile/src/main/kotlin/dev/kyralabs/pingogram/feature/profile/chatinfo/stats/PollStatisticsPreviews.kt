package dev.kyralabs.pingogram.feature.profile.chatinfo.stats

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

// Previews of a poll's statistics page in each of its states.

@PingogramPreviews
@Composable
private fun PollStatisticsPreview() = Page(PollStatisticsPreviewData.content)

@PingogramPreviews
@Composable
private fun PollStatisticsNoGraphPreview() = Page(PollStatisticsPreviewData.noGraph)

@PingogramPreviews
@Composable
private fun PollStatisticsGraphLoadingPreview() = Page(PollStatisticsPreviewData.graphLoading)

@PingogramPreviews
@Composable
private fun PollStatisticsErrorPreview() = Page(PollStatisticsPreviewData.error)

@PingogramPreviews
@Composable
private fun PollStatisticsLoadingPreview() = Page(PollStatisticsUiState.Loading)

@Composable
private fun Page(state: PollStatisticsUiState) {
    PingogramPreview { PollStatisticsScreen(state, onRetry = {}, onBack = {}) }
}
