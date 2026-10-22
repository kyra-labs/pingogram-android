package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toTdChatMemberStatus
import dev.kyralabs.pingogram.core.data.mapper.toTdMessageSender
import dev.kyralabs.pingogram.core.data.mapper.toTdPermissions
import dev.kyralabs.pingogram.core.domain.model.MemberPermissions
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.SlowModeDelays
import dev.kyralabs.pingogram.core.domain.repository.GroupAdministrationRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [GroupAdministrationRepository] on TDLib: `setChatTitle`, `setChatDescription`, `setChatPermissions`,
 * `setChatSlowModeDelay`, `setChatMemberStatus` and `deleteChatHistory`, each sent once as asked. TDLib checks the
 * rights itself and Telegram again; their refusals map to `DomainError.AdminRightsRequired`. A blank title and a slow
 * mode delay Telegram does not accept are refused before anything is sent.
 */
@Singleton
class TdlibGroupAdministrationRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : GroupAdministrationRepository {
        override suspend fun setTitle(
            chatId: Long,
            title: String,
        ): Outcome<Unit> {
            val trimmed = title.trim()
            if (trimmed.isEmpty()) return Outcome.Failure(DomainError.TitleInvalid)
            return client.request(TdApi.SetChatTitle(chatId, trimmed)).map {}
        }

        override suspend fun setDescription(
            chatId: Long,
            description: String,
        ): Outcome<Unit> = client.request(TdApi.SetChatDescription(chatId, description.trim())).map {}

        override suspend fun setPermissions(
            chatId: Long,
            permissions: MemberPermissions,
        ): Outcome<Unit> = client.request(TdApi.SetChatPermissions(chatId, permissions.toTdPermissions())).map {}

        override suspend fun setSlowModeDelay(
            chatId: Long,
            seconds: Int,
        ): Outcome<Unit> {
            if (seconds !in SlowModeDelays) return Outcome.Failure(DomainError.InputRejected)
            return client.request(TdApi.SetChatSlowModeDelay(chatId, seconds)).map {}
        }

        override suspend fun setMemberRole(
            chatId: Long,
            member: MessageSender,
            role: MemberRole,
        ): Outcome<Unit> =
            client
                .request(TdApi.SetChatMemberStatus(chatId, member.toTdMessageSender(), role.toTdChatMemberStatus()))
                .map {}

        /** `deleteChatHistory` without removing the chat from the lists and without revoking for others. */
        override suspend fun clearHistory(chatId: Long): Outcome<Unit> =
            client.request(TdApi.DeleteChatHistory(chatId, false, false)).map {}
    }
