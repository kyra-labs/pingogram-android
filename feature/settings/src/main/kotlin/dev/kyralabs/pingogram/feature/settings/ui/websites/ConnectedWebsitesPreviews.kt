package dev.kyralabs.pingogram.feature.settings.ui.websites

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

@PingogramPreviews
@Composable
private fun ConnectedWebsitesScreenPreview() {
    PingogramPreview { ConnectedWebsitesScreen(ConnectedWebsitesPreviewStates.content, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun ConnectedWebsitesScreenBusyPreview() {
    PingogramPreview { ConnectedWebsitesScreen(ConnectedWebsitesPreviewStates.busy, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun ConnectedWebsitesScreenDialogPreview() {
    PingogramPreview { ConnectedWebsitesScreen(ConnectedWebsitesPreviewStates.dialog, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun ConnectedWebsitesScreenEmptyPreview() {
    PingogramPreview { ConnectedWebsitesScreen(ConnectedWebsitesPreviewStates.empty, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun ConnectedWebsitesScreenLoadingPreview() {
    PingogramPreview { ConnectedWebsitesScreen(ConnectedWebsitesPreviewStates.loading, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun ConnectedWebsitesScreenErrorPreview() {
    PingogramPreview { ConnectedWebsitesScreen(ConnectedWebsitesPreviewStates.error, onEvent = {}, onBack = {}) }
}
