package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.ChatCreationLimits
import dev.kyralabs.pingogram.core.domain.model.CreatedGroup
import dev.kyralabs.pingogram.core.domain.model.MessageAutoDelete
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.domain.repository.ChatCreationRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/**
 * [ChatCreationRepository] on TDLib and the app's upload cache ([UploadFiles]). It caches nothing, as TDLib announces
 * the new chat with `updateNewChat` before it answers, so every other repository has it already.
 *
 * - **Photos** come prepared by `PickedPhotoRepository`. Once the chat exists, `setChatPhoto` uploads it in the
 *   [scope] of the app, as the screen that created the chat leaves meanwhile; the copy is deleted when TDLib answered.
 *   A photo that fails then is left out: the chat stays without one. While creating fails, the copy stays for the
 *   next attempt.
 * - **Groups**: `createNewBasicGroupChat`, whose `failed_to_add_members` names the people Telegram did not add. With
 *   no one to add, or more than a basic group holds, `createNewSupergroupChat` instead (as Telegram's own apps do),
 *   then `addChatMembers` for whom the user picked.
 * - **Channels**: `createNewSupergroupChat` with `is_channel`; `checkChatUsername` and `setSupergroupUsername` make one
 *   public, `supergroupFullInfo.invite_link` (or `replacePrimaryChatInviteLink` while it has none) is a private one's
 *   link.
 *
 * Nothing about the chats (titles, usernames, links, paths) is logged.
 */
@Suppress("TooManyFunctions") // One override per ChatCreationRepository call, and the lookups they share.
@AccountScoped
class TdlibChatCreationRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        private val files: UploadFiles,
        @param:Dispatcher(IO) private val io: CoroutineDispatcher,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) : ChatCreationRepository {
        override suspend fun createGroup(
            title: String,
            userIds: List<Long>,
            autoDelete: MessageAutoDelete,
            photoPath: String?,
        ): Outcome<CreatedGroup> {
            val members = userIds.distinct().take(ChatCreationLimits.MEMBERS_TO_ADD_MAX)
            val created =
                if (members.isEmpty() || members.size >= ChatCreationLimits.BASIC_GROUP_SIZE_MAX) {
                    createSupergroup(title, members, autoDelete)
                } else {
                    client
                        .request(TdApi.CreateNewBasicGroupChat(members.toLongArray(), title.trim(), autoDelete.seconds))
                        .map { CreatedGroup(it.chatId, it.failedToAddMembers.userIds()) }
                }
            if (created is Outcome.Success) setPhoto(created.value.chatId, photoPath)
            return created
        }

        /** A supergroup with [members] added after it was created: none is no failure, only whom Telegram refused. */
        private suspend fun createSupergroup(
            title: String,
            members: List<Long>,
            autoDelete: MessageAutoDelete,
        ): Outcome<CreatedGroup> {
            val function =
                TdApi.CreateNewSupergroupChat(
                    title.trim(),
                    false,
                    false,
                    "",
                    null,
                    autoDelete.seconds,
                    false,
                )
            return when (val created = client.request(function)) {
                is Outcome.Failure -> {
                    created
                }

                is Outcome.Success -> {
                    val chatId = created.value.id
                    // The group exists either way: if adding fails as a whole, every pick counts as not added.
                    val notAdded =
                        if (members.isEmpty()) {
                            persistentListOf()
                        } else {
                            addMembers(chatId, members).getOrNull() ?: members.toImmutableList()
                        }
                    Outcome.Success(CreatedGroup(chatId, notAdded))
                }
            }
        }

        override suspend fun createChannel(
            title: String,
            description: String,
            photoPath: String?,
        ): Outcome<Long> {
            val function = TdApi.CreateNewSupergroupChat(title.trim(), false, true, description.trim(), null, 0, false)
            val created = client.request(function).map { it.id }
            if (created is Outcome.Success) setPhoto(created.value, photoPath)
            return created
        }

        override suspend fun checkUsername(
            chatId: Long,
            username: String,
        ): Outcome<UsernameCheck> =
            client.request(TdApi.CheckChatUsername(chatId, username)).map { it.toUsernameCheck() }

        override suspend fun setUsername(
            chatId: Long,
            username: String,
        ): Outcome<Unit> =
            when (val type = chatType(chatId)) {
                is Outcome.Failure -> {
                    type
                }

                is Outcome.Success -> {
                    val supergroup = type.value as? TdApi.ChatTypeSupergroup
                    if (supergroup == null) {
                        Outcome.Failure(DomainError.NotFound)
                    } else {
                        client.request(TdApi.SetSupergroupUsername(supergroup.supergroupId, username)).map {}
                    }
                }
            }

        override suspend fun inviteLink(chatId: Long): Outcome<String> =
            when (val type = chatType(chatId)) {
                is Outcome.Failure -> {
                    type
                }

                is Outcome.Success -> {
                    val existing = primaryLink(type.value)
                    if (existing != null) {
                        Outcome.Success(existing)
                    } else {
                        client.request(TdApi.ReplacePrimaryChatInviteLink(chatId)).map { it.inviteLink }
                    }
                }
            }

        /**
         * The primary invite link a group or channel of [type] has, from its full info; null without one, or without
         * the full info, as a new primary link is as good.
         */
        private suspend fun primaryLink(type: TdApi.ChatType): String? {
            val link =
                when (type) {
                    is TdApi.ChatTypeSupergroup -> {
                        client.request(TdApi.GetSupergroupFullInfo(type.supergroupId)).getOrNull()?.inviteLink
                    }

                    is TdApi.ChatTypeBasicGroup -> {
                        client.request(TdApi.GetBasicGroupFullInfo(type.basicGroupId)).getOrNull()?.inviteLink
                    }

                    else -> {
                        null
                    }
                }
            return link?.takeIf { !it.isRevoked }?.inviteLink?.ifBlank { null }
        }

        override suspend fun addMembers(
            chatId: Long,
            userIds: List<Long>,
        ): Outcome<ImmutableList<Long>> {
            val members = userIds.distinct().take(ChatCreationLimits.MEMBERS_TO_ADD_MAX)
            if (members.isEmpty()) return Outcome.Success(persistentListOf())
            return client.request(TdApi.AddChatMembers(chatId, members.toLongArray())).map { it.userIds() }
        }

        private suspend fun chatType(chatId: Long): Outcome<TdApi.ChatType> =
            client.request(TdApi.GetChat(chatId)).map { it.type }

        /**
         * Uploads [path] as chat [chatId]'s photo in the app's scope, then deletes the copy, whatever TDLib answered.
         * It waits as long as a photo of a few megabytes may take on a slow network.
         */
        private fun setPhoto(
            chatId: Long,
            path: String?,
        ) {
            if (path == null) return
            scope.launch {
                val photo = TdApi.InputChatPhotoStatic(TdApi.InputFileLocal(path))
                client.request(TdApi.SetChatPhoto(chatId, photo), timeout = PHOTO_UPLOAD_TIMEOUT)
                withContext(io) { files.deleteUploads(listOf(path)) }
            }
        }

        private companion object {
            val PHOTO_UPLOAD_TIMEOUT = 10.minutes
        }
    }

/** The users Telegram did not add; none without a list. */
private fun TdApi.FailedToAddMembers?.userIds(): ImmutableList<Long> =
    this
        ?.failedToAddMembers
        .orEmpty()
        .map { it.userId }
        .distinct()
        .toImmutableList()
