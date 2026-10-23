package dev.kyralabs.pingogram.feature.profile.invite

import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.persistentListOf

/** The join sheet's states for previews and screenshot tests. */
internal object ChatInvitePreviewData {
    val group =
        ChatInviteUiState.Content(
            InviteCard(
                title = "Rust Berlin",
                kind = GroupKind.Supergroup,
                isPublic = false,
                memberCount = 1_284,
                description = "Monthly meetups, talks and help with the borrow checker. Be kind.",
                photo = null,
                avatarId = 1,
                members =
                    persistentListOf(
                        InviteMember(2, UiText.Plain("Ada Lovelace"), null),
                        InviteMember(3, UiText.Plain("Grace Hopper"), null),
                        InviteMember(4, UiText.Plain("Alan Turing"), null),
                    ),
                isVerified = true,
            ),
        )

    val channelRequest =
        ChatInviteUiState.Content(
            InviteCard(
                title = "Kyra Labs news",
                kind = GroupKind.Channel,
                isPublic = false,
                memberCount = 12_400,
                description = "Release notes and previews.",
                photo = null,
                avatarId = 2,
                createsJoinRequest = true,
            ),
        )
}
