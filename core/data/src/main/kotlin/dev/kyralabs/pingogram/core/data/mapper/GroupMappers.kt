package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.BasicGroup
import dev.kyralabs.pingogram.core.domain.model.ChatMemberStatus
import dev.kyralabs.pingogram.core.domain.model.ChatPermissions
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

internal fun TdApi.BasicGroup.toBasicGroup(): BasicGroup =
    BasicGroup(
        id = id,
        memberCount = memberCount,
        status = status.toChatMemberStatus(),
        isActive = isActive,
        // 0: not upgraded.
        upgradedToSupergroupId = upgradedToSupergroupId.takeIf { it != 0L },
    )

internal fun TdApi.Supergroup.toSupergroup(): Supergroup {
    val verification = verificationStatus
    return Supergroup(
        id = id,
        usernames = usernames?.activeUsernames.orEmpty().toImmutableList(),
        memberCount = memberCount,
        status = status.toChatMemberStatus(),
        isChannel = isChannel,
        isForum = isForum,
        isVerified = verification?.isVerified == true,
        isScam = verification?.isScam == true,
        isFake = verification?.isFake == true,
    )
}

/** Null (td_api.tl requires a value, but the field is a Java reference) allows nothing. */
internal fun TdApi.ChatPermissions?.toChatPermissions(): ChatPermissions =
    ChatPermissions(canSendBasicMessages = this?.canSendBasicMessages == true)

internal fun TdApi.ChatMemberStatus?.toChatMemberStatus(): ChatMemberStatus =
    when (this) {
        is TdApi.ChatMemberStatusCreator -> {
            ChatMemberStatus.Creator(isMember)
        }

        is TdApi.ChatMemberStatusAdministrator -> {
            ChatMemberStatus.Administrator(rights?.canPostMessages == true)
        }

        is TdApi.ChatMemberStatusMember -> {
            ChatMemberStatus.Member
        }

        is TdApi.ChatMemberStatusRestricted -> {
            ChatMemberStatus.Restricted(isMember, permissions?.canSendBasicMessages == true)
        }

        is TdApi.ChatMemberStatusBanned -> {
            ChatMemberStatus.Banned
        }

        // ChatMemberStatusLeft, and a status added by a newer TDLib: not a member.
        else -> {
            ChatMemberStatus.Left
        }
    }
