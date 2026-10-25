package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toChatFolderInvite
import dev.kyralabs.pingogram.core.data.mapper.toChatFolderInviteLink
import dev.kyralabs.pingogram.core.data.mapper.toChatFolderSettings
import dev.kyralabs.pingogram.core.data.mapper.toRecommendedChatFolder
import dev.kyralabs.pingogram.core.data.mapper.toTdChatFolder
import dev.kyralabs.pingogram.core.domain.model.ChatFolderInvite
import dev.kyralabs.pingogram.core.domain.model.ChatFolderInviteLink
import dev.kyralabs.pingogram.core.domain.model.ChatFolderSettings
import dev.kyralabs.pingogram.core.domain.model.RecommendedChatFolder
import dev.kyralabs.pingogram.core.domain.repository.ChatFolderRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [ChatFolderRepository] on TDLib's chat folder requests (no cache: `updateChatFolders`, which `TdlibChatRepository`
 * keeps, announces every change of the folders, and a folder's contents are asked for when edited). A folder that
 * includes nothing is refused before it reaches TDLib, with `DomainError.ChatFolderEmpty`.
 */
@Suppress("TooManyFunctions") // One function per TDLib request, as in ChatFolderRepository.
@AccountScoped
class TdlibChatFolderRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : ChatFolderRepository {
        override suspend fun folder(id: Int): Outcome<ChatFolderSettings> =
            client.request(TdApi.GetChatFolder(id)).map { it.toChatFolderSettings() }

        override suspend fun createFolder(settings: ChatFolderSettings): Outcome<Int> =
            if (!settings.includesAnything) {
                Outcome.Failure(DomainError.ChatFolderEmpty)
            } else {
                client.request(TdApi.CreateChatFolder(settings.toTdChatFolder())).map { it.id }
            }

        override suspend fun editFolder(
            id: Int,
            settings: ChatFolderSettings,
        ): Outcome<Unit> =
            if (!settings.includesAnything) {
                Outcome.Failure(DomainError.ChatFolderEmpty)
            } else {
                client.request(TdApi.EditChatFolder(id, settings.toTdChatFolder())).map {}
            }

        override suspend fun chatsToLeave(id: Int): Outcome<List<Long>> =
            client.request(TdApi.GetChatFolderChatsToLeave(id)).map { it.chatIds.toList() }

        override suspend fun deleteFolder(
            id: Int,
            leaveChatIds: List<Long>,
        ): Outcome<Unit> = client.request(TdApi.DeleteChatFolder(id, leaveChatIds.toLongArray())).map {}

        override suspend fun reorderFolders(
            folderIds: List<Int>,
            mainChatListPosition: Int,
        ): Outcome<Unit> = client.request(TdApi.ReorderChatFolders(folderIds.toIntArray(), mainChatListPosition)).map {}

        override suspend fun recommendedFolders(): Outcome<List<RecommendedChatFolder>> =
            client.request(TdApi.GetRecommendedChatFolders()).map { recommended ->
                recommended.chatFolders.map { it.toRecommendedChatFolder() }
            }

        override suspend fun shareableChats(id: Int): Outcome<List<Long>> =
            client.request(TdApi.GetChatsForChatFolderInviteLink(id)).map { it.chatIds.toList() }

        override suspend fun inviteLinks(id: Int): Outcome<List<ChatFolderInviteLink>> =
            client.request(TdApi.GetChatFolderInviteLinks(id)).map { links ->
                links.inviteLinks.map { it.toChatFolderInviteLink() }
            }

        override suspend fun createInviteLink(
            id: Int,
            chatIds: List<Long>,
        ): Outcome<ChatFolderInviteLink> =
            client
                .request(TdApi.CreateChatFolderInviteLink(id, "", chatIds.toLongArray()))
                .map { it.toChatFolderInviteLink() }

        override suspend fun deleteInviteLink(
            id: Int,
            link: String,
        ): Outcome<Unit> = client.request(TdApi.DeleteChatFolderInviteLink(id, link)).map {}

        override suspend fun checkInviteLink(link: String): Outcome<ChatFolderInvite> =
            client.request(TdApi.CheckChatFolderInviteLink(link)).map { it.toChatFolderInvite() }

        override suspend fun addFolderByInviteLink(
            link: String,
            chatIds: List<Long>,
        ): Outcome<Unit> = client.request(TdApi.AddChatFolderByInviteLink(link, chatIds.toLongArray())).map {}
    }
