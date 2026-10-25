package dev.kyralabs.pingogram.feature.settings.ui.lockedchats

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

@Composable
private fun LockedChatsPreview(state: LockedChatsUiState) {
    PingogramPreview {
        LockedChatsScreen(state, onEvent = {}, onOpenChat = {}, onOpenPasscodeLock = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun LockedChatsScreenPreview() {
    LockedChatsPreview(LockedChatsPreviewStates.content)
}

@PingogramPreviews
@Composable
private fun LockedChatsScreenEmptyPreview() {
    LockedChatsPreview(LockedChatsPreviewStates.empty)
}

@PingogramPreviews
@Composable
private fun LockedChatsScreenNoPasscodePreview() {
    LockedChatsPreview(LockedChatsUiState.NoPasscode)
}

@PingogramPreviews
@Composable
private fun LockedChatsPickerPreview() {
    PingogramPreview { LockedChatsPickerContent(LockedChatsPreviewStates.picker, onEvent = {}) }
}
