package dev.kyralabs.pingogram.feature.settings.ui.account.autodelete

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.ui.preview.AccountPagePreviewStates as States

// The previews of the auto-delete page, one per state.

@PingogramPreviews
@Composable
private fun AutoDeleteLoadingPreview() = AutoDelete(States.autoDeleteLoading)

@PingogramPreviews
@Composable
private fun AutoDeleteErrorPreview() = AutoDelete(States.autoDeleteError)

@PingogramPreviews
@Composable
private fun AutoDeletePreview() = AutoDelete(States.autoDelete)

@PingogramPreviews
@Composable
private fun AutoDeleteOtherPreview() = AutoDelete(States.autoDeleteOther)

@PingogramPreviews
@Composable
private fun AutoDeleteSavingPreview() = AutoDelete(States.autoDeleteSaving)

@Composable
private fun AutoDelete(state: AutoDeleteUiState) {
    PingogramPreview { AutoDeleteScreen(state = state, onEvent = {}, onBack = {}) }
}
