package dev.kyralabs.pingogram.feature.profile.chatinfo.requests

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.profile.chatinfo.requests.JoinRequestsPreviewData as Data

// The previews of the join requests page, one per state.

@PingogramPreviews
@Composable
private fun JoinRequestsPreview() = Requests(Data.content)

@PingogramPreviews
@Composable
private fun JoinRequestsChannelPreview() = Requests(Data.channel)

@PingogramPreviews
@Composable
private fun JoinRequestsBusyPreview() = Requests(Data.busy)

@PingogramPreviews
@Composable
private fun JoinRequestsDismissAllPreview() = Requests(Data.dismissAll)

@PingogramPreviews
@Composable
private fun JoinRequestsPageErrorPreview() = Requests(Data.pageError)

@PingogramPreviews
@Composable
private fun JoinRequestsEmptyPreview() = Requests(Data.empty)

@PingogramPreviews
@Composable
private fun JoinRequestsLoadingPreview() = Requests(JoinRequestsUiState.Loading)

@PingogramPreviews
@Composable
private fun JoinRequestsErrorPreview() = Requests(Data.error)

@Composable
private fun Requests(state: JoinRequestsUiState) {
    PingogramPreview { JoinRequestsScreen(state, onEvent = {}, onBack = {}) }
}
