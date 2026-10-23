package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.model.AdministratorRights
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.MemberPermissions
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.ProfileGroup
import dev.kyralabs.pingogram.feature.profile.ui.ProfileRow
import kotlinx.collections.immutable.ImmutableList

// The switches of the rights page (an administrator's rights, a member's restrictions) and the permissions page (what
// members may do by default): which right or permission each is, what it is called, and where it applies.

/** An administrator's right, as a switch of the rights page, for the kinds of groups it applies to. */
enum class AdminRight(
    @param:StringRes val label: Int,
    private val inGroups: Boolean,
    private val inChannels: Boolean,
) {
    ChangeInfo(R.string.chat_info_right_change_info, inGroups = true, inChannels = true),
    PostMessages(R.string.chat_info_right_post_messages, inGroups = false, inChannels = true),
    EditMessages(R.string.chat_info_right_edit_messages, inGroups = false, inChannels = true),
    DeleteMessages(R.string.chat_info_right_delete_messages, inGroups = true, inChannels = true),
    RestrictMembers(R.string.chat_info_right_restrict_members, inGroups = true, inChannels = false),
    InviteUsers(R.string.chat_info_right_invite_users, inGroups = true, inChannels = true),
    PinMessages(R.string.chat_info_right_pin_messages, inGroups = true, inChannels = false),
    ManageVideoChats(R.string.chat_info_right_manage_video_chats, inGroups = true, inChannels = true),
    PostStories(R.string.chat_info_right_post_stories, inGroups = true, inChannels = true),
    EditStories(R.string.chat_info_right_edit_stories, inGroups = true, inChannels = true),
    DeleteStories(R.string.chat_info_right_delete_stories, inGroups = true, inChannels = true),
    ManageDirectMessages(R.string.chat_info_right_manage_direct_messages, inGroups = false, inChannels = true),
    ManageTags(R.string.chat_info_right_manage_tags, inGroups = true, inChannels = false),
    PromoteMembers(R.string.chat_info_right_promote_members, inGroups = true, inChannels = true),
    Anonymous(R.string.chat_info_right_anonymous, inGroups = true, inChannels = false),
    ;

    fun appliesTo(kind: GroupKind): Boolean = if (kind == GroupKind.Channel) inChannels else inGroups

    fun of(rights: AdministratorRights): Boolean =
        when (this) {
            ChangeInfo -> rights.canChangeInfo
            PostMessages -> rights.canPostMessages
            EditMessages -> rights.canEditMessages
            DeleteMessages -> rights.canDeleteMessages
            RestrictMembers -> rights.canRestrictMembers
            InviteUsers -> rights.canInviteUsers
            PinMessages -> rights.canPinMessages
            ManageVideoChats -> rights.canManageVideoChats
            PostStories -> rights.canPostStories
            EditStories -> rights.canEditStories
            DeleteStories -> rights.canDeleteStories
            ManageDirectMessages -> rights.canManageDirectMessages
            ManageTags -> rights.canManageTags
            PromoteMembers -> rights.canPromoteMembers
            Anonymous -> rights.isAnonymous
        }

    fun set(
        rights: AdministratorRights,
        on: Boolean,
    ): AdministratorRights =
        when (this) {
            ChangeInfo -> rights.copy(canChangeInfo = on)
            PostMessages -> rights.copy(canPostMessages = on)
            EditMessages -> rights.copy(canEditMessages = on)
            DeleteMessages -> rights.copy(canDeleteMessages = on)
            RestrictMembers -> rights.copy(canRestrictMembers = on)
            InviteUsers -> rights.copy(canInviteUsers = on)
            PinMessages -> rights.copy(canPinMessages = on)
            ManageVideoChats -> rights.copy(canManageVideoChats = on)
            PostStories -> rights.copy(canPostStories = on)
            EditStories -> rights.copy(canEditStories = on)
            DeleteStories -> rights.copy(canDeleteStories = on)
            ManageDirectMessages -> rights.copy(canManageDirectMessages = on)
            ManageTags -> rights.copy(canManageTags = on)
            PromoteMembers -> rights.copy(canPromoteMembers = on)
            Anonymous -> rights.copy(isAnonymous = on)
        }
}

/** What members may do, as a switch of the permissions and rights pages (groups only; channels have none). */
enum class Permission(
    @param:StringRes val label: Int,
) {
    SendMessages(R.string.chat_info_permission_send_messages),
    SendPhotos(R.string.chat_info_permission_send_photos),
    SendVideos(R.string.chat_info_permission_send_videos),
    SendFiles(R.string.chat_info_permission_send_files),
    SendMusic(R.string.chat_info_permission_send_music),
    SendVoiceMessages(R.string.chat_info_permission_send_voice),
    SendVideoMessages(R.string.chat_info_permission_send_video_messages),
    SendStickers(R.string.chat_info_permission_send_stickers),
    SendPolls(R.string.chat_info_permission_send_polls),
    AddLinkPreviews(R.string.chat_info_permission_link_previews),
    React(R.string.chat_info_permission_react),
    AddMembers(R.string.chat_info_permission_add_members),
    PinMessages(R.string.chat_info_permission_pin_messages),
    ChangeInfo(R.string.chat_info_permission_change_info),
    ;

    fun of(permissions: MemberPermissions): Boolean =
        when (this) {
            SendMessages -> permissions.canSendBasicMessages
            SendPhotos -> permissions.canSendPhotos
            SendVideos -> permissions.canSendVideos
            SendFiles -> permissions.canSendDocuments
            SendMusic -> permissions.canSendAudios
            SendVoiceMessages -> permissions.canSendVoiceNotes
            SendVideoMessages -> permissions.canSendVideoNotes
            SendStickers -> permissions.canSendOtherMessages
            SendPolls -> permissions.canSendPolls
            AddLinkPreviews -> permissions.canAddLinkPreviews
            React -> permissions.canReactToMessages
            AddMembers -> permissions.canInviteUsers
            PinMessages -> permissions.canPinMessages
            ChangeInfo -> permissions.canChangeInfo
        }

    fun set(
        permissions: MemberPermissions,
        on: Boolean,
    ): MemberPermissions =
        when (this) {
            SendMessages -> permissions.copy(canSendBasicMessages = on)
            SendPhotos -> permissions.copy(canSendPhotos = on)
            SendVideos -> permissions.copy(canSendVideos = on)
            SendFiles -> permissions.copy(canSendDocuments = on)
            SendMusic -> permissions.copy(canSendAudios = on)
            SendVoiceMessages -> permissions.copy(canSendVoiceNotes = on)
            SendVideoMessages -> permissions.copy(canSendVideoNotes = on)
            SendStickers -> permissions.copy(canSendOtherMessages = on)
            SendPolls -> permissions.copy(canSendPolls = on)
            AddLinkPreviews -> permissions.copy(canAddLinkPreviews = on)
            React -> permissions.copy(canReactToMessages = on)
            AddMembers -> permissions.copy(canInviteUsers = on)
            PinMessages -> permissions.copy(canPinMessages = on)
            ChangeInfo -> permissions.copy(canChangeInfo = on)
        }
}

/**
 * A switch of a rights or permissions page: its [label], whether it is on, and whether the user may change it (an
 * administrator grants only rights they have; a member is never allowed more than the group's defaults).
 */
@Immutable
data class RightSwitch(
    val key: String,
    @param:StringRes val label: Int,
    val checked: Boolean,
    val enabled: Boolean = true,
)

/** The switches of [items] as one segmented group under [title] ([SwitchRow]); toggling one calls [onToggle]. */
@Composable
internal fun RightsGroup(
    title: String,
    items: ImmutableList<RightSwitch>,
    onToggle: (key: String, on: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows =
        items.map { item ->
            ProfileRow(item.key) { shapes ->
                SwitchRow(
                    label = stringResource(item.label),
                    checked = item.checked,
                    onCheckedChange = { onToggle(item.key, it) },
                    shapes = shapes,
                    enabled = item.enabled,
                    modifier = Modifier.testTag(item.key),
                )
            }
        }
    ProfileGroup(rows, modifier, title = title)
}
