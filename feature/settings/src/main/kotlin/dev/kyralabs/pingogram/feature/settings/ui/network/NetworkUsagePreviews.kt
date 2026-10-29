package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.ui.preview.NetworkUsagePreviewStates as States

// The previews of the Network usage page, one per state.

@PingogramPreviews
@Composable
private fun NetworkUsagePreview() = Usage(States.wifi)

@PingogramPreviews
@Composable
private fun NetworkUsageEmptyPreview() = Usage(States.roaming)

@PingogramPreviews
@Composable
private fun NetworkUsageResetDialogPreview() = Usage(States.resetDialog)

@PingogramPreviews
@Composable
private fun NetworkUsageResettingPreview() = Usage(States.resetting)

@PingogramPreviews
@Composable
private fun NetworkUsageLoadingPreview() = Usage(States.loading)

@PingogramPreviews
@Composable
private fun NetworkUsageErrorPreview() = Usage(States.error)

@Composable
private fun Usage(state: NetworkUsageUiState) {
    PingogramPreview { NetworkUsageScreen(state, onEvent = {}, onBack = {}) }
}
