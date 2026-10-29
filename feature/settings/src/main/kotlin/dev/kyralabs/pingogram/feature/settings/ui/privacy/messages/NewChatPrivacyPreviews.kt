package dev.kyralabs.pingogram.feature.settings.ui.privacy.messages

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.ui.preview.PrivacyPreviewStates as States

// The previews of "Who can message me", one per state (loading shows nothing for its first 200 ms, so none).

@PingogramPreviews
@Composable
private fun NewChatPrivacyPreview() = Preview(States.newChats)

@PingogramPreviews
@Composable
private fun NewChatPrivacyRestrictedPreview() = Preview(States.newChatsRestricted)

@PingogramPreviews
@Composable
private fun NewChatPrivacyWithoutPremiumPreview() = Preview(States.newChatsWithoutPremium)

@PingogramPreviews
@Composable
private fun NewChatPrivacyPaidPreview() = Preview(States.newChatsPaid)

@PingogramPreviews
@Composable
private fun NewChatPrivacySavingPreview() = Preview(States.newChatsSaving)

@PingogramPreviews
@Composable
private fun NewChatPrivacyErrorPreview() = Preview(States.newChatsError)

@Composable
private fun Preview(state: NewChatPrivacyUiState) {
    PingogramPreview { NewChatPrivacyScreen(state = state, onEvent = {}, onBack = {}) }
}
