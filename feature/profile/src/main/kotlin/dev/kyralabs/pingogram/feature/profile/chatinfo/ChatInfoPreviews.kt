@file:Suppress("TooManyFunctions") // One preview per page and state.

package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.preview.PingogramVariantPreviews
import dev.kyralabs.pingogram.feature.profile.chatinfo.preview.ChatInfoPreviewData
import dev.kyralabs.pingogram.feature.profile.ui.SharedMediaActions
import dev.kyralabs.pingogram.feature.profile.ui.SharedMediaUiState
import dev.kyralabs.pingogram.feature.profile.ui.preview.ProfilePreviewData

// Previews of the info page of groups and channels and the pages it opens, in each of their states.

@Composable
private fun ChatInfoPreviewContent(
    state: ChatInfoUiState,
    media: SharedMediaUiState = ProfilePreviewData.media,
    twoPanes: Boolean = false,
) {
    PingogramPreview {
        ChatInfoScreen(
            state = state,
            media = media,
            onEvent = {},
            onMediaEvent = {},
            onBack = {},
            mediaActions = SharedMediaActions({ _, _ -> }, { _, _ -> }, {}),
            twoPanes = twoPanes,
        )
    }
}

@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun ChatInfoScreenGroupPreview() {
    ChatInfoPreviewContent(ChatInfoPreviewData.group)
}

@PingogramPreviews
@Composable
private fun ChatInfoScreenChannelPreview() {
    ChatInfoPreviewContent(ChatInfoPreviewData.channel, ProfilePreviewData.emptyMedia)
}

@PingogramPreviews
@Composable
private fun ChatInfoScreenLoadingPreview() {
    ChatInfoPreviewContent(ChatInfoUiState.Loading)
}

@PingogramPreviews
@Composable
private fun ChatInfoScreenErrorPreview() {
    ChatInfoPreviewContent(ChatInfoUiState.Error(DomainError.NotFound))
}

@Preview(name = "Expanded", widthDp = EXPANDED_PREVIEW_WIDTH, heightDp = EXPANDED_PREVIEW_HEIGHT)
@Composable
private fun ChatInfoScreenExpandedPreview() {
    ChatInfoPreviewContent(ChatInfoPreviewData.group, twoPanes = true)
}

@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun ChatMembersScreenPreview() {
    PingogramPreview { ChatMembersScreen(ChatInfoPreviewData.membersPage, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun ChatMembersScreenEmptyPreview() {
    PingogramPreview { ChatMembersScreen(ChatInfoPreviewData.membersEmpty, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun ChatMembersScreenLoadingPreview() {
    PingogramPreview { ChatMembersScreen(ChatMembersUiState(), onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun ChatMembersScreenErrorPreview() {
    PingogramPreview {
        ChatMembersScreen(ChatMembersUiState(list = MembersList.Error(DomainError.Network)), onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun MemberRightsScreenPreview() {
    PingogramPreview { MemberRightsScreen(ChatInfoPreviewData.adminRights, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun MemberRestrictionsScreenPreview() {
    PingogramPreview { MemberRightsScreen(ChatInfoPreviewData.restrictions, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun MemberRightsScreenErrorPreview() {
    PingogramPreview { MemberRightsScreen(MemberRightsUiState.Error(DomainError.Network), onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun ChatPermissionsScreenPreview() {
    PingogramPreview { ChatPermissionsScreen(ChatInfoPreviewData.permissions, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun ChatPermissionsScreenLoadingPreview() {
    PingogramPreview { ChatPermissionsScreen(ChatPermissionsUiState.Loading, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun EditChatInfoScreenPreview() {
    PingogramPreview {
        EditChatInfoScreen(
            EditChatInfoUiState.Content(
                kind = ChatInfoPreviewData.group.kind,
                title = "Rust Berlin",
                description =
                    ChatInfoPreviewData.group.description
                        ?.text
                        .orEmpty(),
            ),
            onEvent = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun InviteLinksScreenPreview() {
    PingogramPreview { InviteLinksScreen(ChatInfoPreviewData.inviteLinks, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun InviteLinksScreenEmptyPreview() {
    PingogramPreview { InviteLinksScreen(InviteLinksUiState.Content(), onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun InviteLinksScreenErrorPreview() {
    PingogramPreview { InviteLinksScreen(InviteLinksUiState.Error(DomainError.Network), onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun EditInviteLinkScreenPreview() {
    PingogramPreview { EditInviteLinkScreen(ChatInfoPreviewData.newLink, onEvent = {}, onBack = {}) }
}

private const val EXPANDED_PREVIEW_WIDTH = 1000
private const val EXPANDED_PREVIEW_HEIGHT = 800
