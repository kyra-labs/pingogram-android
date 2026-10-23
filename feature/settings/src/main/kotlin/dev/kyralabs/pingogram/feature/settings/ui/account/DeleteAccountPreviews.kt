package dev.kyralabs.pingogram.feature.settings.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.ui.preview.DeleteAccountPreviewStates

// The previews of the Delete account page, one per state.

@PingogramPreviews
@Composable
private fun DeleteAccountScreenPreview() {
    PingogramPreview { DeleteAccountScreen(state = DeleteAccountPreviewStates.withPassword, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun DeleteAccountScreenWithoutPasswordPreview() {
    PingogramPreview {
        DeleteAccountScreen(state = DeleteAccountPreviewStates.withoutPassword, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun DeleteAccountScreenDeletingPreview() {
    PingogramPreview { DeleteAccountScreen(state = DeleteAccountPreviewStates.deleting, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun DeleteAccountScreenConfirmingPreview() {
    PingogramPreview { DeleteAccountScreen(state = DeleteAccountPreviewStates.confirming, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun DeleteAccountScreenWrongPasswordPreview() {
    PingogramPreview {
        DeleteAccountScreen(state = DeleteAccountPreviewStates.wrongPassword, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun DeleteAccountScreenFloodWaitPreview() {
    PingogramPreview { DeleteAccountScreen(state = DeleteAccountPreviewStates.floodWait, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun DeleteAccountScreenLoadingPreview() {
    PingogramPreview { DeleteAccountScreen(state = DeleteAccountUiState.Loading, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun DeleteAccountScreenErrorPreview() {
    PingogramPreview {
        DeleteAccountScreen(state = DeleteAccountUiState.Error(DomainError.Network), onEvent = {}, onBack = {})
    }
}

@Preview(name = "Expanded", widthDp = 840, heightDp = 891)
@Composable
private fun DeleteAccountScreenExpandedPreview() {
    PingogramPreview { DeleteAccountScreen(state = DeleteAccountPreviewStates.withPassword, onEvent = {}, onBack = {}) }
}
