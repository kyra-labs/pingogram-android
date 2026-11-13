package dev.kyralabs.pingogram.feature.profile.chatinfo

import dev.kyralabs.pingogram.core.domain.model.AdministratorRights
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.GroupMember
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.model.isAdministrator
import dev.kyralabs.pingogram.core.domain.model.isOnlineAt
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.core.ui.time.UserStatusFormatter
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.Instant
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What the current user's rights let them do on a group's or channel's member pages: their [rights], the group's
 * [kind] and their own id, whose row gets only "Set tag", where [canEditOwnTag] (the group's permissions allow it) or
 * the rights do.
 */
internal data class MemberPowers(
    val kind: GroupKind,
    val rights: AdministratorRights,
    val myUserId: Long?,
    val canEditOwnTag: Boolean = false,
) {
    /** Whether the user may tag people in this group: groups only, with `canManageTags`. */
    val canTagOthers: Boolean get() = kind != GroupKind.Channel && rights.canManageTags
}

/**
 * The rows of members (`REQUIREMENTS.md` FR-P3), on the info page and the members page: a user by their name as
 * `UserRepository` knows it now (the status as the conversation says it, on [statusFormatter]'s clock), a chat by its
 * title; the role's label; and the actions [MemberPowers] allow.
 */
@Suppress("LongParameterList") // The member, what the app knows of them, the user's powers, and the clock.
internal fun memberItem(
    member: GroupMember,
    user: User?,
    chat: Chat?,
    powers: MemberPowers,
    statusFormatter: UserStatusFormatter,
    now: Instant,
): MemberItem =
    when (val sender = member.sender) {
        is MessageSender.User -> {
            val isDeleted = user == null || user.isDeleted
            val isBot = user?.type == UserType.Bot
            MemberItem(
                sender = sender,
                name =
                    if (user == null ||
                        isDeleted
                    ) {
                        UiText.resource(UiR.string.user_deleted_account)
                    } else {
                        user.fullName()
                    },
                photo = user?.profilePhoto,
                avatarId = sender.userId,
                placeholderIcon = if (isDeleted) UiR.drawable.ic_person_filled else null,
                status =
                    when {
                        user == null || isDeleted -> null
                        isBot -> UiText.resource(R.string.profile_status_bot)
                        else -> statusFormatter.status(user.status, now)
                    },
                isOnline = user != null && !isDeleted && !isBot && user.status.isOnlineAt(now.epochSecond),
                role = member.roleLabel(),
                tag = member.tag.takeIf { it.isNotBlank() },
                isAdministrator = member.role.isAdministrator,
                actions =
                    if (sender.userId == powers.myUserId) {
                        powers.ownActions()
                    } else {
                        powers.actionsOn(member, isDeleted)
                    },
            )
        }

        is MessageSender.Chat -> {
            MemberItem(
                sender = sender,
                name = UiText.Plain(chat?.title.orEmpty()),
                photo = chat?.photo,
                avatarId = sender.chatId,
                role = member.roleLabel(),
                tag = member.tag.takeIf { it.isNotBlank() },
                isAdministrator = member.role.isAdministrator,
                // A chat can only be banned, or let back.
                actions =
                    if (member.role is MemberRole.Banned && powers.rights.canRestrictMembers) {
                        persistentListOf(MemberAction.Unban)
                    } else {
                        persistentListOf()
                    },
            )
        }
    }

/** The label at the end of a member's row: what their role is; null for a plain member. */
private fun GroupMember.roleLabel(): UiText? =
    when (role) {
        is MemberRole.Owner -> UiText.resource(R.string.chat_info_role_owner)
        is MemberRole.Administrator -> UiText.resource(R.string.chat_info_role_admin)
        is MemberRole.Restricted -> UiText.resource(R.string.chat_info_role_restricted)
        is MemberRole.Banned -> UiText.resource(R.string.chat_info_role_removed)
        MemberRole.Member, MemberRole.Left -> null
    }

/** What the user may do on their own row: set their tag, where the group or their rights allow it. */
private fun MemberPowers.ownActions(): ImmutableList<MemberAction> =
    if (kind != GroupKind.Channel && (canEditOwnTag || rights.canManageTags)) {
        persistentListOf(MemberAction.SetTag)
    } else {
        persistentListOf()
    }

/**
 * What these powers allow on [member]: a tag set on a member or an editable administrator in a group (not a deleted
 * account's); an editable administrator's rights changed or taken; a member promoted, restricted (supergroups only) or
 * removed; restrictions changed or lifted; a removed user let back. Nothing on the owner, and nothing without the
 * rights.
 */
private fun MemberPowers.actionsOn(
    member: GroupMember,
    isDeleted: Boolean,
): ImmutableList<MemberAction> {
    val tag = MemberAction.SetTag.takeIf { canTagOthers && !isDeleted }
    val promote = rights.canPromoteMembers
    val restrict = rights.canRestrictMembers
    val canRestrict = restrict && kind == GroupKind.Supergroup
    val actions =
        when (val role = member.role) {
            is MemberRole.Owner, MemberRole.Left -> {
                emptyList()
            }

            is MemberRole.Administrator -> {
                listOfNotNull(
                    tag.takeIf { role.canBeEdited },
                    MemberAction.EditRights.takeIf { role.canBeEdited && promote },
                    MemberAction.Dismiss.takeIf { role.canBeEdited && promote },
                )
            }

            MemberRole.Member -> {
                listOfNotNull(
                    tag,
                    MemberAction.Promote.takeIf { promote },
                    MemberAction.Restrict.takeIf { canRestrict },
                    MemberAction.Remove.takeIf { restrict },
                )
            }

            is MemberRole.Restricted -> {
                listOfNotNull(
                    tag.takeIf { role.isMember },
                    MemberAction.Restrict.takeIf { canRestrict },
                    MemberAction.Unrestrict.takeIf { canRestrict },
                    MemberAction.Remove.takeIf { restrict },
                )
            }

            is MemberRole.Banned -> {
                listOfNotNull(MemberAction.Unban.takeIf { restrict })
            }
        }
    return actions.toImmutableList()
}
