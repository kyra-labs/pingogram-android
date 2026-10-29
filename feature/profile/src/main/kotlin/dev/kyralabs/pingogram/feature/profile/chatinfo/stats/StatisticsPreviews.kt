package dev.kyralabs.pingogram.feature.profile.chatinfo.stats

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

@PingogramPreviews
@Composable
private fun StatisticsChannelPreview() {
    PingogramPreview { StatisticsScreen(StatisticsPreviewData.channel, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun StatisticsSupergroupPreview() {
    PingogramPreview { StatisticsScreen(StatisticsPreviewData.supergroup, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun StatisticsGraphLoadingPreview() {
    PingogramPreview { StatisticsScreen(StatisticsPreviewData.graphLoading, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun StatisticsErrorPreview() {
    PingogramPreview { StatisticsScreen(StatisticsPreviewData.error, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun StatisticsLoadingPreview() {
    PingogramPreview { StatisticsScreen(StatisticsUiState.Loading, onEvent = {}, onBack = {}) }
}
