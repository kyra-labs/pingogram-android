package dev.kyralabs.pingogram.feature.profile.chatinfo.community

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import kotlinx.collections.immutable.persistentListOf

/** Sample community pages for previews and goldens. */
internal object CommunityPreviewData {
    val owner =
        CommunityUiState.Content(
            header =
                CommunityHeader(
                    id = 77,
                    name = "Kyra Labs Community",
                    photo = null,
                    role = CommunityRoleLabel.Owner,
                    canRename = true,
                    canDelete = true,
                ),
            chats =
                persistentListOf(
                    CommunityChatItem(
                        chatId = 1,
                        title = "Kyra Labs Updates",
                        photo = null,
                        shape = AvatarShape.Channel,
                        kind = CommunityChatKind.Channel,
                        memberCount = 3_904,
                        membership = Membership.Joined,
                    ),
                    CommunityChatItem(
                        chatId = 2,
                        title = "Pingogram Beta",
                        photo = null,
                        shape = AvatarShape.Group,
                        kind = CommunityChatKind.Group,
                        memberCount = 1_120,
                        membership = Membership.Joined,
                    ),
                    CommunityChatItem(
                        chatId = 3,
                        title = "Spenzia Waitlist",
                        photo = null,
                        shape = AvatarShape.Group,
                        kind = CommunityChatKind.Group,
                        memberCount = 640,
                        membership = Membership.CanJoin,
                    ),
                    CommunityChatItem(
                        chatId = 4,
                        title = "Kyra Build Bot",
                        photo = null,
                        shape = AvatarShape.Circle,
                        kind = CommunityChatKind.Bot,
                    ),
                    CommunityChatItem(
                        chatId = 5,
                        title = "Moderators",
                        photo = null,
                        shape = AvatarShape.Group,
                        kind = CommunityChatKind.Group,
                        memberCount = 6,
                        isHidden = true,
                        membership = Membership.Joined,
                    ),
                ),
        )

    val member =
        owner.copy(
            header = owner.header.copy(role = null, canRename = false, canDelete = false),
            chats =
                persistentListOf(
                    owner.chats[0],
                    owner.chats[2].copy(membership = Membership.Joining),
                    owner.chats[3],
                ),
        )
}

/** The page's states: the owner's, a member's while joining, loading and a failure. */
internal class CommunityStates : PreviewParameterProvider<CommunityUiState> {
    override val values: Sequence<CommunityUiState> =
        sequenceOf(
            CommunityPreviewData.owner,
            CommunityPreviewData.member,
            CommunityUiState.Loading,
            CommunityUiState.Error(DomainError.Network),
        )
}

@PingogramPreviews
@Composable
private fun CommunityScreenPreview(
    @PreviewParameter(CommunityStates::class) state: CommunityUiState,
) {
    PingogramPreview { CommunityScreen(state, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun RenameCommunityDialogPreview() {
    PingogramPreview { RenameCommunityDialog("Kyra Labs Community", isBusy = false, onEvent = {}) }
}
