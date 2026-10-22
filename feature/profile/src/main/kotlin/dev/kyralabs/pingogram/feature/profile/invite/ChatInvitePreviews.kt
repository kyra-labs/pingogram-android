package dev.kyralabs.pingogram.feature.profile.invite

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

@PingogramPreviews
@Composable
private fun ChatInviteGroupPreview() {
    PingogramPreview { ChatInviteContent(ChatInvitePreviewData.group, {}, {}) }
}

@PingogramPreviews
@Composable
private fun ChatInviteChannelRequestPreview() {
    PingogramPreview { ChatInviteContent(ChatInvitePreviewData.channelRequest, {}, {}) }
}

@PingogramPreviews
@Composable
private fun ChatInviteRequestSentPreview() {
    PingogramPreview { ChatInviteContent(ChatInviteUiState.RequestSent(GroupKind.Channel), {}, {}) }
}

@PingogramPreviews
@Composable
private fun ChatInviteFailedPreview() {
    PingogramPreview { ChatInviteContent(ChatInviteUiState.Failed(DomainError.InviteLinkInvalid, false), {}, {}) }
}

@PingogramPreviews
@Composable
private fun ChatInviteLoadingPreview() {
    PingogramPreview { ChatInviteContent(ChatInviteUiState.Loading, {}, {}) }
}
