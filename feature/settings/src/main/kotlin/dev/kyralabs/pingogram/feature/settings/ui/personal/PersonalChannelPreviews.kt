package dev.kyralabs.pingogram.feature.settings.ui.personal

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

@PingogramPreviews
@Composable
private fun PersonalChannelScreenPreview() {
    PingogramPreview { PersonalChannelScreen(PersonalChannelPreviewStates.content, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun PersonalChannelScreenSavingPreview() {
    PingogramPreview { PersonalChannelScreen(PersonalChannelPreviewStates.saving, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun PersonalChannelScreenNoChannelsPreview() {
    PingogramPreview { PersonalChannelScreen(PersonalChannelPreviewStates.noChannels, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun PersonalChannelScreenLoadingPreview() {
    PingogramPreview { PersonalChannelScreen(PersonalChannelPreviewStates.loading, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun PersonalChannelScreenErrorPreview() {
    PingogramPreview { PersonalChannelScreen(PersonalChannelPreviewStates.error, onEvent = {}, onBack = {}) }
}
