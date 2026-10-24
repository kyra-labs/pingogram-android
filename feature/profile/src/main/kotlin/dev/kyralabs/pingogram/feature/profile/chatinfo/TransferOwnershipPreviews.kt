package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.profile.chatinfo.preview.ChatInfoPreviewData

@PingogramPreviews
@Composable
private fun OwnerRightsScreenPreview() {
    PingogramPreview { MemberRightsScreen(ChatInfoPreviewData.ownerRights, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun TransferOwnershipDialogPreview() {
    PingogramPreview {
        MemberRightsScreen(
            ChatInfoPreviewData.ownerRights,
            onEvent = {},
            onBack = {},
            transfer = ChatInfoPreviewData.transferDialog,
        )
    }
}

@PingogramPreviews
@Composable
private fun TransferWrongPasswordPreview() {
    PingogramPreview {
        MemberRightsScreen(
            ChatInfoPreviewData.ownerRights,
            onEvent = {},
            onBack = {},
            transfer = ChatInfoPreviewData.transferWrongPassword,
            transferPassword = remember { TextFieldState("hunter2") },
        )
    }
}

@PingogramPreviews
@Composable
private fun TransferringPreview() {
    PingogramPreview {
        MemberRightsScreen(
            ChatInfoPreviewData.ownerRights,
            onEvent = {},
            onBack = {},
            transfer = ChatInfoPreviewData.transferring,
            transferPassword = remember { TextFieldState("hunter2") },
        )
    }
}
