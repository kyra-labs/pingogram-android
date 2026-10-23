package dev.kyralabs.pingogram.feature.settings.ui.folders.invite

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.ui.preview.FolderInvitePreviewStates

// The shared folder sheet's content in each of its states, without the sheet's window.

@Composable
private fun Preview(state: FolderInviteUiState) {
    PingogramPreview { FolderInviteContent(state = state, onEvent = {}, onCancel = {}) }
}

@PingogramPreviews
@Composable
private fun FolderInviteNewFolderPreview() = Preview(FolderInvitePreviewStates.newFolder)

@PingogramPreviews
@Composable
private fun FolderInviteNewChatsPreview() = Preview(FolderInvitePreviewStates.newChats)

@PingogramPreviews
@Composable
private fun FolderInviteAddedPreview() = Preview(FolderInvitePreviewStates.added)

@PingogramPreviews
@Composable
private fun FolderInviteAddingPreview() = Preview(FolderInvitePreviewStates.adding)

@PingogramPreviews
@Composable
private fun FolderInviteFailedPreview() = Preview(FolderInvitePreviewStates.offline)

@PingogramPreviews
@Composable
private fun FolderInviteLoadingPreview() = Preview(FolderInviteUiState.Loading)
