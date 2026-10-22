package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.AdministratorRights
import dev.kyralabs.pingogram.core.domain.model.GroupMember
import dev.kyralabs.pingogram.core.domain.model.InviteLink
import dev.kyralabs.pingogram.core.domain.model.MemberPermissions
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import org.drinkless.tdlib.TdApi

// The info page of groups and channels (REQUIREMENTS.md FR-P3): roles, rights, permissions, members and invite links,
// every field both ways, so a right or permission Pingogram does not show survives a change of another.

/** Null (td_api.tl requires a value, but the field is a Java reference) and a status of a newer TDLib: not a member. */
internal fun TdApi.ChatMemberStatus?.toMemberRole(): MemberRole =
    when (this) {
        is TdApi.ChatMemberStatusCreator -> {
            MemberRole.Owner(isMember, isAnonymous)
        }

        is TdApi.ChatMemberStatusAdministrator -> {
            MemberRole.Administrator(canBeEdited, rights.toAdministratorRights())
        }

        is TdApi.ChatMemberStatusMember -> {
            MemberRole.Member
        }

        is TdApi.ChatMemberStatusRestricted -> {
            MemberRole.Restricted(isMember, restrictedUntilDate.toLong(), permissions.toMemberPermissions())
        }

        is TdApi.ChatMemberStatusBanned -> {
            MemberRole.Banned(bannedUntilDate.toLong())
        }

        else -> {
            MemberRole.Left
        }
    }

/** The status that gives [this] role; the owner's is the current one, as ownership is not transferred here. */
internal fun MemberRole.toTdChatMemberStatus(): TdApi.ChatMemberStatus =
    when (this) {
        is MemberRole.Owner -> {
            TdApi.ChatMemberStatusCreator(isAnonymous, isMember)
        }

        is MemberRole.Administrator -> {
            TdApi.ChatMemberStatusAdministrator(canBeEdited, rights.toTdRights())
        }

        MemberRole.Member -> {
            TdApi.ChatMemberStatusMember(0)
        }

        is MemberRole.Restricted -> {
            TdApi.ChatMemberStatusRestricted(isMember, untilDate.toInt(), permissions.toTdPermissions())
        }

        MemberRole.Left -> {
            TdApi.ChatMemberStatusLeft()
        }

        is MemberRole.Banned -> {
            TdApi.ChatMemberStatusBanned(untilDate.toInt())
        }
    }

internal fun TdApi.ChatAdministratorRights?.toAdministratorRights(): AdministratorRights =
    if (this == null) {
        AdministratorRights.None
    } else {
        AdministratorRights(
            canManageChat = canManageChat,
            canChangeInfo = canChangeInfo,
            canPostMessages = canPostMessages,
            canEditMessages = canEditMessages,
            canDeleteMessages = canDeleteMessages,
            canInviteUsers = canInviteUsers,
            canRestrictMembers = canRestrictMembers,
            canPinMessages = canPinMessages,
            canManageTopics = canManageTopics,
            canPromoteMembers = canPromoteMembers,
            canManageVideoChats = canManageVideoChats,
            canPostStories = canPostStories,
            canEditStories = canEditStories,
            canDeleteStories = canDeleteStories,
            canManageDirectMessages = canManageDirectMessages,
            canManageTags = canManageTags,
            canSendWelcomeMessages = canSendWelcomeMessages,
            isAnonymous = isAnonymous,
        )
    }

internal fun AdministratorRights.toTdRights(): TdApi.ChatAdministratorRights =
    TdApi.ChatAdministratorRights(
        canManageChat,
        canChangeInfo,
        canPostMessages,
        canEditMessages,
        canDeleteMessages,
        canInviteUsers,
        canRestrictMembers,
        canPinMessages,
        canManageTopics,
        canPromoteMembers,
        canManageVideoChats,
        canPostStories,
        canEditStories,
        canDeleteStories,
        canManageDirectMessages,
        canManageTags,
        canSendWelcomeMessages,
        isAnonymous,
    )

/** Null allows nothing. */
internal fun TdApi.ChatPermissions?.toMemberPermissions(): MemberPermissions =
    if (this == null) {
        MemberPermissions.None
    } else {
        MemberPermissions(
            canSendBasicMessages = canSendBasicMessages,
            canSendAudios = canSendAudios,
            canSendDocuments = canSendDocuments,
            canSendPhotos = canSendPhotos,
            canSendVideos = canSendVideos,
            canSendVideoNotes = canSendVideoNotes,
            canSendVoiceNotes = canSendVoiceNotes,
            canSendPolls = canSendPolls,
            canSendOtherMessages = canSendOtherMessages,
            canAddLinkPreviews = canAddLinkPreviews,
            canReactToMessages = canReactToMessages,
            canEditTag = canEditTag,
            canChangeInfo = canChangeInfo,
            canInviteUsers = canInviteUsers,
            canPinMessages = canPinMessages,
            canCreateTopics = canCreateTopics,
        )
    }

internal fun MemberPermissions.toTdPermissions(): TdApi.ChatPermissions =
    TdApi.ChatPermissions(
        canSendBasicMessages,
        canSendAudios,
        canSendDocuments,
        canSendPhotos,
        canSendVideos,
        canSendVideoNotes,
        canSendVoiceNotes,
        canSendPolls,
        canSendOtherMessages,
        canAddLinkPreviews,
        canReactToMessages,
        canEditTag,
        canChangeInfo,
        canInviteUsers,
        canPinMessages,
        canCreateTopics,
    )

/** Null for a member without a sender (td_api.tl requires one) or with a sender of a newer TDLib. */
internal fun TdApi.ChatMember.toGroupMember(): GroupMember? {
    val sender = memberId?.toMessageSender() ?: return null
    return GroupMember(
        sender = sender,
        role = status.toMemberRole(),
        tag = tag.orEmpty(),
        joinedDate = joinedChatDate.toLong(),
    )
}

internal fun TdApi.ChatInviteLink.toInviteLink(): InviteLink =
    InviteLink(
        link = inviteLink.orEmpty(),
        name = name.orEmpty(),
        creatorUserId = creatorUserId,
        date = date.toLong(),
        expirationDate = expirationDate.toLong(),
        memberLimit = memberLimit,
        memberCount = memberCount,
        pendingJoinRequestCount = pendingJoinRequestCount,
        createsJoinRequest = createsJoinRequest,
        isPrimary = isPrimary,
        isRevoked = isRevoked,
        isSubscription = subscriptionPricing != null,
    )
