package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toInviteLink
import dev.kyralabs.pingogram.core.domain.model.InviteLink
import dev.kyralabs.pingogram.core.domain.model.InviteLinkSettings
import dev.kyralabs.pingogram.core.domain.model.InviteLinksPage
import dev.kyralabs.pingogram.core.domain.repository.InviteLinkRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [InviteLinkRepository] on TDLib: `getChatInviteLinks` (paged by the date and link of the last one),
 * `getChatInviteLink`, `createChatInviteLink`, `editChatInviteLink`, `revokeChatInviteLink`,
 * `deleteRevokedChatInviteLink` and `deleteAllRevokedChatInviteLinks`. Settings are clamped to what Telegram accepts
 * before they are sent: a name of at most 32 characters, a member limit of at most 99,999 and none with join requests
 * (td_api.tl `createChatInviteLink`). The links themselves are never logged.
 */
@Singleton
class TdlibInviteLinkRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : InviteLinkRepository {
        override suspend fun links(
            chatId: Long,
            creatorUserId: Long,
            revoked: Boolean,
            after: InviteLink?,
            limit: Int,
        ): Outcome<InviteLinksPage> =
            client
                .request(
                    TdApi.GetChatInviteLinks(
                        chatId,
                        creatorUserId,
                        revoked,
                        after?.date?.toInt() ?: 0,
                        after?.link.orEmpty(),
                        limit,
                    ),
                ).map { found ->
                    InviteLinksPage(
                        links =
                            found.inviteLinks
                                .orEmpty()
                                .mapNotNull { it?.toInviteLink() }
                                .toImmutableList(),
                        totalCount = found.totalCount.coerceAtLeast(0),
                    )
                }

        override suspend fun link(
            chatId: Long,
            link: String,
        ): Outcome<InviteLink> = client.request(TdApi.GetChatInviteLink(chatId, link)).map { it.toInviteLink() }

        override suspend fun create(
            chatId: Long,
            settings: InviteLinkSettings,
        ): Outcome<InviteLink> {
            val valid = settings.clamped()
            return client
                .request(
                    TdApi.CreateChatInviteLink(
                        chatId,
                        valid.name,
                        valid.expirationDate.toInt(),
                        valid.memberLimit,
                        valid.createsJoinRequest,
                    ),
                ).map { it.toInviteLink() }
        }

        override suspend fun edit(
            chatId: Long,
            link: String,
            settings: InviteLinkSettings,
        ): Outcome<InviteLink> {
            val valid = settings.clamped()
            return client
                .request(
                    TdApi.EditChatInviteLink(
                        chatId,
                        link,
                        valid.name,
                        valid.expirationDate.toInt(),
                        valid.memberLimit,
                        valid.createsJoinRequest,
                    ),
                ).map { it.toInviteLink() }
        }

        override suspend fun revoke(
            chatId: Long,
            link: String,
        ): Outcome<Unit> = client.request(TdApi.RevokeChatInviteLink(chatId, link)).map {}

        override suspend fun deleteRevoked(
            chatId: Long,
            link: String,
        ): Outcome<Unit> = client.request(TdApi.DeleteRevokedChatInviteLink(chatId, link)).map {}

        override suspend fun deleteAllRevoked(
            chatId: Long,
            creatorUserId: Long,
        ): Outcome<Unit> = client.request(TdApi.DeleteAllRevokedChatInviteLinks(chatId, creatorUserId)).map {}
    }

/** [this] as Telegram accepts it: the name cut to its maximum, the member limit in range and none with requests. */
private fun InviteLinkSettings.clamped(): InviteLinkSettings =
    copy(
        name = name.trim().take(InviteLinkSettings.NAME_LENGTH_MAX),
        expirationDate = expirationDate.coerceAtLeast(0),
        memberLimit = if (createsJoinRequest) 0 else memberLimit.coerceIn(0, InviteLinkSettings.MEMBER_LIMIT_MAX),
    )
