package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.Community
import dev.kyralabs.pingogram.core.domain.model.CommunityChat
import dev.kyralabs.pingogram.core.domain.model.CommunityDetails
import dev.kyralabs.pingogram.core.domain.model.CommunityRole
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

internal fun TdApi.Community.toCommunity(): Community =
    Community(
        id = id,
        name = name,
        photo = photo?.toProfilePhoto(),
        hasAccess = haveAccess,
        role = status.toCommunityRole(),
        canEditChatList = permissions?.canEditChatList == true,
    )

internal fun TdApi.CommunityMemberStatus?.toCommunityRole(): CommunityRole =
    when (this) {
        is TdApi.CommunityMemberStatusCreator -> {
            CommunityRole.Owner
        }

        is TdApi.CommunityMemberStatusAdministrator -> {
            CommunityRole.Administrator(
                canChangeInfo = rights?.canChangeInfo == true,
                canEditChatList = rights?.canEditChatList == true,
            )
        }

        is TdApi.CommunityMemberStatusMember -> {
            CommunityRole.Member
        }

        is TdApi.CommunityMemberStatusBanned -> {
            CommunityRole.Banned
        }

        else -> {
            CommunityRole.Left
        }
    }

internal fun TdApi.CommunityFullInfo.toCommunityDetails(): CommunityDetails =
    CommunityDetails(
        chats =
            chats.orEmpty().map { CommunityChat(it.chatId, it.canViewHistory, it.isHidden) }.toImmutableList(),
        administratorCount = administratorCount,
    )
