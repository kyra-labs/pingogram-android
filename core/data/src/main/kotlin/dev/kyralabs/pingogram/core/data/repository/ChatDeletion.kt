package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toChatMemberStatus
import dev.kyralabs.pingogram.core.domain.model.DeleteChatAction
import dev.kyralabs.pingogram.core.domain.model.DeleteChatOptions
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi

/**
 * How to delete a chat (`ChatRepository.deleteOptions`, `deleteChat`), from what TDLib reports about it now:
 * `chat.can_be_deleted_only_for_self` and `can_be_deleted_for_all_users`, which have no update of their own, and the
 * current user's [status] in a basic group or supergroup (null for other chats).
 *
 * What [requests] sends, checked against td/telegram at the pinned TDLib version:
 * - **Private chat**: `deleteChatHistory(remove_from_chat_list, revoke)`; TDLib itself deletes only for self where
 *   revoking is not allowed (`MessagesManager::delete_dialog_history`).
 * - **Secret chat**: an active secret chat can be deleted only for both sides (`MessagesManager::can_delete_dialog`).
 *   For everyone, `deleteChat` closes it and has its history deleted on both sides, which removes it from the lists
 *   (`DialogManager::delete_dialog`, `SecretChatActor::cancel_chat` with `delete_history`). For self only,
 *   `closeSecretChat` first (unless it is closed already), then `deleteChatHistory`, which a closed secret chat
 *   allows for self only. `closeSecretChat` closes the chat locally at once but answers only when Telegram answered
 *   (`SecretChatActor::do_close_chat_impl`): offline, the deletion fails with `DomainError.Network`, and deleting
 *   the chat again, now closed, takes only `deleteChatHistory`.
 * - **Basic group**: `leaveChat` removes the user (`DialogParticipantManager::delete_chat_participant`) but leaves
 *   the group and its history in the lists (a basic group leaves them only once deactivated,
 *   `DialogManager::is_dialog_removed_from_dialog_list`), so `deleteChatHistory(remove_from_chat_list)` follows.
 *   A group the user is no longer in, or a deactivated one, needs only the latter. For everyone, `deleteChat`
 *   deletes the group, which only its owner may (`ChatManager::delete_chat`).
 * - **Supergroup or channel**: `leaveChat` alone removes it from the lists, as TDLib lists none the user is not a
 *   member of. An owner who leaves stays the owner (`DialogParticipantManager::restrict_channel_participant`); only
 *   `deleteChat` deletes it, for everyone (`ChatManager::delete_channel`). The only listed one the user is not a
 *   member of is a public service announcement, which `deleteChatHistory(remove_from_chat_list)` hides.
 */
internal class ChatDeletion(
    private val chatId: Long,
    private val type: TdApi.ChatType,
    private val status: TdApi.ChatMemberStatus?,
    private val canBeDeletedOnlyForSelf: Boolean,
    private val canBeDeletedForAllUsers: Boolean,
) {
    private val isGroup = type is TdApi.ChatTypeBasicGroup || type is TdApi.ChatTypeSupergroup

    private val isMember = status.toChatMemberStatus().isMember

    val options: DeleteChatOptions =
        DeleteChatOptions(
            action = if (isGroup && isMember) DeleteChatAction.Leave else DeleteChatAction.Delete,
            canDeleteForEveryone = canBeDeletedForAllUsers,
        )

    /** The requests that delete the chat, in order; [forEveryone] counts only where [options] allow it. */
    fun requests(forEveryone: Boolean): List<TdApi.Function<TdApi.Ok>> {
        val forAll = forEveryone && canBeDeletedForAllUsers
        return when {
            type is TdApi.ChatTypePrivate -> listOf(TdApi.DeleteChatHistory(chatId, true, forAll))

            // A secret chat, group or channel.
            forAll -> listOf(TdApi.DeleteChat(chatId))

            else -> requestsForSelf()
        }
    }

    /** The requests that remove the chat for the current user only. */
    private fun requestsForSelf(): List<TdApi.Function<TdApi.Ok>> {
        val removeFromLists = TdApi.DeleteChatHistory(chatId, true, false)
        return when (type) {
            is TdApi.ChatTypeSecret -> {
                // Closed already, or not yet.
                if (canBeDeletedOnlyForSelf) {
                    listOf(removeFromLists)
                } else {
                    listOf(TdApi.CloseSecretChat(type.secretChatId), removeFromLists)
                }
            }

            is TdApi.ChatTypeBasicGroup -> {
                if (isMember) listOf(TdApi.LeaveChat(chatId), removeFromLists) else listOf(removeFromLists)
            }

            is TdApi.ChatTypeSupergroup -> {
                when {
                    isMember -> listOf(TdApi.LeaveChat(chatId))

                    canBeDeletedOnlyForSelf -> listOf(removeFromLists)

                    // In no list: nothing to remove.
                    else -> emptyList()
                }
            }

            // A chat type added by a newer TDLib.
            else -> {
                listOf(removeFromLists)
            }
        }
    }
}

/**
 * The [ChatDeletion] of chat [chatId], from `getChat` and, for groups, `getBasicGroup` or `getSupergroup`: TDLib
 * answers all of them from memory.
 */
internal suspend fun TdlibClient.chatDeletion(chatId: Long): Outcome<ChatDeletion> {
    val chat =
        when (val result = request(TdApi.GetChat(chatId))) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return result
        }
    val groupStatus: Outcome<TdApi.ChatMemberStatus?> =
        when (val type = chat.type) {
            is TdApi.ChatTypeBasicGroup -> request(TdApi.GetBasicGroup(type.basicGroupId)).map { it.status }
            is TdApi.ChatTypeSupergroup -> request(TdApi.GetSupergroup(type.supergroupId)).map { it.status }
            else -> Outcome.Success(null)
        }
    return groupStatus.map { status ->
        ChatDeletion(chat.id, chat.type, status, chat.canBeDeletedOnlyForSelf, chat.canBeDeletedForAllUsers)
    }
}

/** Sends [functions] one after another, stopping at the first that fails. */
internal suspend fun TdlibClient.requestAll(functions: List<TdApi.Function<TdApi.Ok>>): Outcome<Unit> {
    for (function in functions) {
        val result = request(function)
        if (result is Outcome.Failure) return result
    }
    return Outcome.Success(Unit)
}
