package dev.kyralabs.pingogram.feature.profile.chatinfo.preview

import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.MemberFilter
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.component.PhotoTask
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.chatinfo.AdminRight
import dev.kyralabs.pingogram.feature.profile.chatinfo.ChatAdministration
import dev.kyralabs.pingogram.feature.profile.chatinfo.ChatInfoActions
import dev.kyralabs.pingogram.feature.profile.chatinfo.ChatInfoUiState
import dev.kyralabs.pingogram.feature.profile.chatinfo.ChatLink
import dev.kyralabs.pingogram.feature.profile.chatinfo.ChatMembersUiState
import dev.kyralabs.pingogram.feature.profile.chatinfo.ChatPermissionsUiState
import dev.kyralabs.pingogram.feature.profile.chatinfo.EditChatInfoUiState
import dev.kyralabs.pingogram.feature.profile.chatinfo.EditInviteLinkUiState
import dev.kyralabs.pingogram.feature.profile.chatinfo.InviteLinkItem
import dev.kyralabs.pingogram.feature.profile.chatinfo.InviteLinksUiState
import dev.kyralabs.pingogram.feature.profile.chatinfo.MemberAction
import dev.kyralabs.pingogram.feature.profile.chatinfo.MemberItem
import dev.kyralabs.pingogram.feature.profile.chatinfo.MemberRightsUiState
import dev.kyralabs.pingogram.feature.profile.chatinfo.MembersList
import dev.kyralabs.pingogram.feature.profile.chatinfo.MembersPreview
import dev.kyralabs.pingogram.feature.profile.chatinfo.MoreMembers
import dev.kyralabs.pingogram.feature.profile.chatinfo.Permission
import dev.kyralabs.pingogram.feature.profile.chatinfo.RightSwitch
import dev.kyralabs.pingogram.feature.profile.chatinfo.key
import dev.kyralabs.pingogram.feature.profile.ui.ProfileHeader
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Groups and channels for previews and screenshot tests: a public group the user administers, a private channel the
 * user reads, its members, and the administrators' pages.
 */
object ChatInfoPreviewData {
    private const val CHAT_ID = -100L

    private const val DESCRIPTION = "Rust meetups in Berlin every second Tuesday. Talks and slides at rust.berlin"
    private const val LINK = "rust.berlin"

    private fun member(
        id: Long,
        name: String,
        status: UiText? = UiText.resource(UiR.string.user_status_online),
        role: UiText? = null,
        actions: List<MemberAction> = listOf(MemberAction.Promote, MemberAction.Restrict, MemberAction.Remove),
    ) = MemberItem(
        sender = MessageSender.User(id),
        name = UiText.Plain(name),
        photo = null,
        avatarId = id,
        status = status,
        isOnline = status == UiText.resource(UiR.string.user_status_online),
        role = role,
        actions = actions.toImmutableList(),
    )

    val members =
        persistentListOf(
            member(1, "Grace Hopper", role = UiText.resource(R.string.chat_info_role_owner), actions = emptyList()),
            member(2, "Ada Lovelace", role = UiText.Plain("Moderator"), actions = listOf(MemberAction.EditRights)),
            member(3, "Alan Turing", status = UiText.Plain("last seen 5 minutes ago")),
            member(4, "Katherine Johnson", status = UiText.Plain("last seen yesterday at 9:41 PM")),
            member(5, "Edsger Dijkstra", status = UiText.Plain("last seen recently")),
        )

    val group =
        ChatInfoUiState.Content(
            header =
                ProfileHeader(
                    userId = CHAT_ID,
                    name = UiText.Plain("Rust Berlin"),
                    photo = null,
                    status = UiText.Plain("Public group · 1,024 members"),
                    isVerified = true,
                ),
            kind = GroupKind.Supergroup,
            description =
                FormattedText(
                    DESCRIPTION,
                    persistentListOf(TextEntity(DESCRIPTION.indexOf(LINK), LINK.length, TextEntityType.Url)),
                ),
            link = ChatLink(url = "https://t.me/rustberlin", text = "t.me/rustberlin", isPublic = true),
            actions = ChatInfoActions(isMuted = false, canLeave = true, canDelete = true, canClearHistory = true),
            members = MembersPreview(members, totalCount = 1_024),
            administration =
                ChatAdministration(
                    canEditInfo = true,
                    canEditPermissions = true,
                    administratorCount = 3,
                    canManageInviteLinks = true,
                    bannedCount = 2,
                    restrictedCount = 1,
                ),
        )

    val channel =
        ChatInfoUiState.Content(
            header =
                ProfileHeader(
                    userId = CHAT_ID - 1,
                    name = UiText.Plain("Compose Weekly"),
                    photo = null,
                    status = UiText.Plain("Private channel · 18,302 subscribers"),
                ),
            kind = GroupKind.Channel,
            description = FormattedText("What changed in Jetpack Compose this week.", persistentListOf()),
            actions = ChatInfoActions(isMuted = true, canLeave = true),
        )

    val membersPage =
        ChatMembersUiState(
            kind = GroupKind.Supergroup,
            filter = MemberFilter.Recent,
            filters =
                persistentListOf(
                    MemberFilter.Recent,
                    MemberFilter.Administrators,
                    MemberFilter.Restricted,
                    MemberFilter.Banned,
                ),
            list = MembersList.Content(members, totalCount = 1_024, more = MoreMembers.Available),
        )

    val membersEmpty = membersPage.copy(query = "zz", list = MembersList.Empty)

    val adminRights =
        MemberRightsUiState.Content(
            name = UiText.Plain("Alan Turing"),
            photo = null,
            avatarId = 3,
            restrict = false,
            isNew = true,
            switches =
                AdminRight.entries
                    .filter { it.appliesTo(GroupKind.Supergroup) }
                    .map {
                        RightSwitch(
                            key = it.key,
                            label = it.label,
                            checked = it != AdminRight.PromoteMembers && it != AdminRight.Anonymous,
                            enabled = it != AdminRight.Anonymous,
                        )
                    }.toImmutableList(),
            canRevoke = false,
        )

    /** Editing the group's photo, name and description, without a photo. */
    val editInfo =
        EditChatInfoUiState.Content(
            kind = group.kind,
            title = "Rust Berlin",
            description = group.description?.text.orEmpty(),
            avatarId = group.header.userId,
        )

    /** The same with a photo whose new one uploads. */
    val editInfoUploading =
        editInfo.copy(
            photo = ProfilePhoto(small = FileRef(1, 0), big = FileRef(2, 0), minithumbnail = null),
            photoTask = PhotoTask.Uploading(progress = 0.6f),
        )

    val restrictions =
        MemberRightsUiState.Content(
            name = UiText.Plain("Alan Turing"),
            photo = null,
            avatarId = 3,
            restrict = true,
            isNew = false,
            switches =
                Permission.entries
                    .map {
                        RightSwitch(
                            it.key,
                            it.label,
                            checked = it.ordinal < 4,
                            enabled = it != Permission.ChangeInfo,
                        )
                    }.toImmutableList(),
            canRevoke = true,
        )

    val permissions =
        ChatPermissionsUiState.Content(
            switches =
                Permission.entries
                    .map { RightSwitch(it.key, it.label, checked = it != Permission.ChangeInfo) }
                    .toImmutableList(),
            slowModeDelay = 30,
        )

    val inviteLinks =
        InviteLinksUiState.Content(
            primary =
                InviteLinkItem(
                    link = "https://t.me/+AbCdEfGhIjKlMnOp",
                    title = "t.me/+AbCdEfGhIjKlMnOp",
                    detail = UiText.Plain("41 joined"),
                    isActive = true,
                    canEdit = false,
                ),
            links =
                persistentListOf(
                    InviteLinkItem(
                        link = "https://t.me/+QrStUvWx",
                        title = "Meetup flyer",
                        detail = UiText.Plain("12 joined · Expires Oct 30"),
                        isActive = true,
                        canEdit = true,
                    ),
                    InviteLinkItem(
                        link = "https://t.me/+YzAbCdEf",
                        title = "Speakers",
                        detail = UiText.Plain("3 joined · Full"),
                        isActive = false,
                        canEdit = true,
                    ),
                ),
            revoked =
                persistentListOf(
                    InviteLinkItem(
                        link = "https://t.me/+OldLink",
                        title = "Last year",
                        detail = UiText.Plain("87 joined · Revoked"),
                        isActive = false,
                        canEdit = false,
                    ),
                ),
        )

    val newLink = EditInviteLinkUiState.Content(isNew = true, name = "Meetup flyer")
}
