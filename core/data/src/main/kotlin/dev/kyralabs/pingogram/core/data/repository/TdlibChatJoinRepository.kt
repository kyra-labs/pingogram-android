package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.ChatInvite
import dev.kyralabs.pingogram.core.domain.model.DirectMessagesChat
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.JoinResult
import dev.kyralabs.pingogram.core.domain.repository.ChatJoinRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [ChatJoinRepository] on TDLib's `checkChatInviteLink`, `joinChatByInviteLink` and `joinChat`, each a request to
 * Telegram, and a channel's `supergroupFullInfo` for its direct messages chat. No cache: an invitation is checked once
 * per sheet, and TDLib announces the chat joined with its updates.
 *
 * Telegram answers a request to join with `INVITE_REQUEST_SENT` where TDLib does not turn it into
 * `chatJoinResultRequestSent` itself; both are [JoinResult.RequestSent].
 */
@AccountScoped
class TdlibChatJoinRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : ChatJoinRepository {
        override suspend fun checkInvite(inviteLink: String): Outcome<ChatInvite> =
            client.request(TdApi.CheckChatInviteLink(inviteLink)).map { it.toChatInvite() }

        override suspend fun joinByInvite(inviteLink: String): Outcome<JoinResult> =
            client.request(TdApi.JoinChatByInviteLink(inviteLink)).toJoinResult()

        override suspend fun join(chatId: Long): Outcome<JoinResult> =
            client.request(TdApi.JoinChat(chatId)).toJoinResult()

        override suspend fun directMessagesChat(supergroupId: Long): Outcome<DirectMessagesChat> =
            when (val info = client.request(TdApi.GetSupergroupFullInfo(supergroupId))) {
                is Outcome.Failure -> {
                    info
                }

                is Outcome.Success -> {
                    val chatId = info.value.directMessagesChatId
                    // getChat makes TDLib announce the chat before the conversation opens it.
                    if (chatId == 0L) {
                        Outcome.Failure(DomainError.NotFound)
                    } else {
                        client.request(TdApi.GetChat(chatId)).directMessagesChat()
                    }
                }
            }
    }

/** The chat TDLib answered, if it is a supergroup's (as direct messages chats are); `DomainError.NotFound` if not. */
private fun Outcome<TdApi.Chat>.directMessagesChat(): Outcome<DirectMessagesChat> =
    when (this) {
        is Outcome.Failure -> {
            this
        }

        is Outcome.Success -> {
            (value.type as? TdApi.ChatTypeSupergroup)
                ?.let { Outcome.Success(DirectMessagesChat(value.id, it.supergroupId)) }
                ?: Outcome.Failure(DomainError.NotFound)
        }
    }

private fun TdApi.ChatInviteLinkInfo.toChatInvite(): ChatInvite {
    val verification = verificationStatus
    return ChatInvite(
        chatId = chatId,
        kind =
            when (type) {
                is TdApi.InviteLinkChatTypeBasicGroup -> GroupKind.BasicGroup
                is TdApi.InviteLinkChatTypeChannel -> GroupKind.Channel
                else -> GroupKind.Supergroup
            },
        title = title,
        photo = photo?.toProfilePhoto(),
        description = description.orEmpty(),
        memberCount = memberCount,
        memberUserIds = memberUserIds?.toList().orEmpty().toImmutableList(),
        previewSeconds = accessibleFor,
        createsJoinRequest = createsJoinRequest,
        isPublic = isPublic,
        isVerified = verification?.isVerified == true,
        isScam = verification?.isScam == true,
        isFake = verification?.isFake == true,
        needsSubscription = subscriptionInfo != null,
    )
}

private fun Outcome<TdApi.ChatJoinResult>.toJoinResult(): Outcome<JoinResult> =
    when (this) {
        is Outcome.Success -> {
            Outcome.Success(
                when (val result = value) {
                    is TdApi.ChatJoinResultSuccess -> JoinResult.Joined(result.chatId)
                    is TdApi.ChatJoinResultRequestSent -> JoinResult.RequestSent
                    is TdApi.ChatJoinResultGuardBotApprovalRequired -> JoinResult.NeedsBotApproval
                    else -> JoinResult.Declined
                },
            )
        }

        is Outcome.Failure -> {
            val error = error
            if (error is DomainError.Unknown && error.message.startsWith(INVITE_REQUEST_SENT)) {
                Outcome.Success(JoinResult.RequestSent)
            } else {
                this
            }
        }
    }

/** Telegram's answer to a request to join a chat that administrators approve. */
private const val INVITE_REQUEST_SENT = "INVITE_REQUEST_SENT"
