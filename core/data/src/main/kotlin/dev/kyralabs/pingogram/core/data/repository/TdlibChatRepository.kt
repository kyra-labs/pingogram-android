package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.isMuted
import dev.kyralabs.pingogram.core.data.mapper.ownMute
import dev.kyralabs.pingogram.core.data.mapper.resolveMute
import dev.kyralabs.pingogram.core.data.mapper.toChat
import dev.kyralabs.pingogram.core.data.mapper.toChatDraft
import dev.kyralabs.pingogram.core.data.mapper.toChatFolder
import dev.kyralabs.pingogram.core.data.mapper.toChatListType
import dev.kyralabs.pingogram.core.data.mapper.toChatPermissions
import dev.kyralabs.pingogram.core.data.mapper.toChatPosition
import dev.kyralabs.pingogram.core.data.mapper.toChatPositions
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.data.mapper.toMessageSender
import dev.kyralabs.pingogram.core.data.mapper.toNotificationScope
import dev.kyralabs.pingogram.core.data.mapper.toProfilePhoto
import dev.kyralabs.pingogram.core.data.mapper.toTdChatList
import dev.kyralabs.pingogram.core.data.mapper.toUnreadChatCount
import dev.kyralabs.pingogram.core.data.mapper.withOwnMute
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.DeleteChatOptions
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ChatRepository] on TDLib's chats. TDLib announces each chat with `updateNewChat` before any of its ids reaches the
 * app, then reports each change of a field in an update of its own, so every chat is kept for the session: as many
 * as TDLib loaded for lists, messages and searches. Chat folders and the unread counts of the lists come from their
 * own updates.
 *
 * [Chat.isMuted] is resolved whenever the chat's own setting or the default of its kind of chat
 * (`updateScopeNotificationSettings`) changes; a changed default re-resolves every chat that follows it.
 *
 * [chatList] sorts the chats of the list again whenever a chat changed, on [dispatcher]: a sort of about 1000 chats
 * costs far less than a frame, and keeping a sorted copy of every list (main, archive, each folder) up to date per
 * update would cost more code and memory than it saves. Updates that change no chat (folders, counts) sort nothing.
 *
 * Actions on chats only send requests: TDLib applies each change locally and reports it in an update before it tells
 * Telegram, so the cache follows without guessing; so do opening and closing a chat. One function per action, as in
 * [ChatRepository] (hence `TooManyFunctions`).
 */
@Suppress("TooManyFunctions")
@Singleton
class TdlibChatRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
        @Dispatcher(Default) private val dispatcher: CoroutineDispatcher,
        private val openChats: OpenChats,
    ) : ChatRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, ChatCache(), ChatCache::reduce)

        override fun chatList(list: ChatListType): Flow<ImmutableList<Chat>> =
            store.state
                .map { it.chats }
                // A new map is a changed chat; a changed folder or count keeps the map.
                .distinctUntilChanged { old, new -> old === new }
                .map { chats -> chats.sortedIn(list) }
                .distinctUntilChanged()
                .flowOn(dispatcher)

        override suspend fun loadChats(
            list: ChatListType,
            limit: Int,
        ): Outcome<Boolean> =
            when (val result = client.request(TdApi.LoadChats(list.toTdChatList(), limit))) {
                is Outcome.Success -> Outcome.Success(true)

                // TDLib answers 404 once every chat of the list is loaded, and "Chat list not found" for a folder
                // deleted meanwhile, which has nothing more to load either.
                is Outcome.Failure -> if (result.error == DomainError.NotFound) Outcome.Success(false) else result
            }

        override fun chat(id: Long): Flow<Chat?> = store.state.map { it.chats[id]?.chat }.distinctUntilChanged()

        override suspend fun getChat(id: Long): Outcome<Chat> {
            val cached = store.state.value.chats[id]
            if (cached != null) return Outcome.Success(cached.chat)
            // The answer is not stored: TDLib sends updateNewChat before it, and a later update may overtake it here.
            return when (val result = client.request(TdApi.GetChat(id))) {
                is Outcome.Success -> {
                    val chat = result.value.toChat(store.state.value.mutedScopes)
                    if (chat != null) Outcome.Success(chat) else Outcome.Failure(DomainError.NotFound)
                }

                is Outcome.Failure -> {
                    result
                }
            }
        }

        /** `createPrivateChat` without `force`: TDLib knows the user, or fails. */
        override suspend fun privateChat(userId: Long): Outcome<Chat> =
            when (val result = client.request(TdApi.CreatePrivateChat(userId, false))) {
                is Outcome.Success -> {
                    val chat = result.value.toChat(store.state.value.mutedScopes)
                    if (chat != null) Outcome.Success(chat) else Outcome.Failure(DomainError.NotFound)
                }

                is Outcome.Failure -> {
                    result
                }
            }

        override val folders: Flow<ImmutableList<ChatFolder>> =
            store.state.map { it.folders }.distinctUntilChanged()

        override val mainChatListPosition: Flow<Int> =
            store.state.map { it.mainChatListPosition }.distinctUntilChanged()

        override fun unreadChatCount(list: ChatListType): Flow<UnreadChatCount> =
            store.state.map { it.unreadChatCounts[list] ?: UnreadChatCount.None }.distinctUntilChanged()

        /** `toggleChatIsPinned`. */
        override suspend fun setPinned(
            chatId: Long,
            list: ChatListType,
            isPinned: Boolean,
        ): Outcome<Unit> = client.request(TdApi.ToggleChatIsPinned(list.toTdChatList(), chatId, isPinned)).map {}

        /**
         * `setChatNotificationSettings` with the chat's current settings, as the last update reported them, with only
         * the mute changed; TDLib replaces every setting. A mute of more than 366 days lasts forever
         * (`get_mute_until`, td/telegram/DialogNotificationSettings.cpp), and an own mute of 0 unmutes the chat even
         * while its kind of chat is muted by default. TDLib refuses it for Saved Messages.
         */
        override suspend fun setMuted(
            chatId: Long,
            isMuted: Boolean,
        ): Outcome<Unit> {
            val cached = store.state.value.chats[chatId] ?: return Outcome.Failure(DomainError.NotFound)
            val settings = cached.notificationSettings.withOwnMute(isMuted)
            return client.request(TdApi.SetChatNotificationSettings(chatId, settings)).map {}
        }

        /** `addChatToList`, which also takes the chat out of the other one of main list and archive. */
        override suspend fun setArchived(
            chatId: Long,
            isArchived: Boolean,
        ): Outcome<Unit> {
            val list = if (isArchived) TdApi.ChatListArchive() else TdApi.ChatListMain()
            return client.request(TdApi.AddChatToList(chatId, list)).map {}
        }

        /** The requests of [markAsReadRequests] for the chat as cached, in order. */
        override suspend fun markAsRead(chatId: Long): Outcome<Unit> {
            val cached = store.state.value.chats[chatId] ?: return Outcome.Failure(DomainError.NotFound)
            return client.requestAll(cached.chat.markAsReadRequests())
        }

        /** `toggleChatIsMarkedAsUnread`. */
        override suspend fun markAsUnread(chatId: Long): Outcome<Unit> =
            client.request(TdApi.ToggleChatIsMarkedAsUnread(chatId, true)).map {}

        /** See [ChatDeletion]. */
        override suspend fun deleteOptions(chatId: Long): Outcome<DeleteChatOptions> =
            client.chatDeletion(chatId).map { it.options }

        /** See [ChatDeletion]; read again here, as what the chat allows may have changed since [deleteOptions]. */
        override suspend fun deleteChat(
            chatId: Long,
            forEveryone: Boolean,
        ): Outcome<Unit> =
            when (val deletion = client.chatDeletion(chatId)) {
                is Outcome.Success -> client.requestAll(deletion.value.requests(forEveryone))
                is Outcome.Failure -> deletion
            }

        /** `openChat`; the chat counts as open ([OpenChats]) from now on, whether TDLib accepts it or not. */
        override suspend fun openChat(chatId: Long): Outcome<Unit> {
            openChats.open(chatId)
            return client.request(TdApi.OpenChat(chatId)).map {}
        }

        /** `closeChat`; the counterpart of [openChat] in [OpenChats] too. */
        override suspend fun closeChat(chatId: Long): Outcome<Unit> {
            openChats.close(chatId)
            return client.request(TdApi.CloseChat(chatId)).map {}
        }

        /**
         * `viewMessages` from the chat's history without `force_read`: TDLib reads them because the chat is open,
         * as the official apps do while the conversation shows.
         */
        override suspend fun viewMessages(
            chatId: Long,
            messageIds: List<Long>,
        ): Outcome<Unit> =
            client
                .request(
                    TdApi.ViewMessages(chatId, messageIds.toLongArray(), TdApi.MessageSourceChatHistory(), false),
                ).map {}
    }

/**
 * A chat, and its notification settings: the own mute among them resolves [Chat.isMuted] again when a default changes,
 * and [TdlibChatRepository.setMuted] keeps the others. TDLib's object, never changed, as it stays in `core:data`.
 */
private data class CachedChat(
    val chat: Chat,
    val notificationSettings: TdApi.ChatNotificationSettings,
) {
    val ownMute: Boolean? get() = notificationSettings.ownMute
}

private data class ChatCache(
    val chats: PersistentMap<Long, CachedChat> = persistentMapOf(),
    val mutedScopes: PersistentSet<NotificationScope> = persistentSetOf(),
    val folders: ImmutableList<ChatFolder> = persistentListOf(),
    /** Where "All chats" goes among the [folders]; see [ChatRepository.mainChatListPosition]. */
    val mainChatListPosition: Int = 0,
    /**
     * By chat list. Counts of deleted folders stay until TDLib closes: they are a few numbers, and TDLib may count a
     * new folder before `updateChatFolders` announces it.
     */
    val unreadChatCounts: PersistentMap<ChatListType, UnreadChatCount> = persistentMapOf(),
) {
    fun reduce(update: TdApi.Update): ChatCache =
        when (update) {
            is TdApi.UpdateNewChat -> withNewChat(update.chat)
            is TdApi.UpdateChatNotificationSettings -> withSettings(update.chatId, update.notificationSettings)
            is TdApi.UpdateScopeNotificationSettings -> withScopeMute(update.scope, update.notificationSettings.isMuted)
            is TdApi.UpdateChatFolders -> withFolders(update)
            is TdApi.UpdateUnreadChatCount -> withUnreadChatCount(update)
            else -> chatChange(update)?.let(::withChange) ?: this
        }

    private fun withNewChat(tdChat: TdApi.Chat): ChatCache {
        val chat = tdChat.toChat(mutedScopes) ?: return this
        return copy(chats = chats.putting(chat.id, CachedChat(chat, tdChat.notificationSettings)))
    }

    /** Updates for unknown chats are ignored: TDLib announces every chat before it reports a change. */
    private fun withChange(change: ChatChange): ChatCache {
        val cached = chats[change.chatId] ?: return this
        return copy(chats = chats.putting(change.chatId, cached.copy(chat = change.apply(cached.chat))))
    }

    private fun withSettings(
        chatId: Long,
        settings: TdApi.ChatNotificationSettings,
    ): ChatCache {
        val cached = chats[chatId] ?: return this
        val chat = cached.chat.copy(isMuted = resolveMute(settings.ownMute, cached.chat.type, mutedScopes))
        return copy(chats = chats.putting(chatId, CachedChat(chat, settings)))
    }

    private fun withScopeMute(
        tdScope: TdApi.NotificationSettingsScope,
        scopeMuted: Boolean,
    ): ChatCache {
        val scope = tdScope.toNotificationScope() ?: return this
        val scopes = if (scopeMuted) mutedScopes.adding(scope) else mutedScopes.removing(scope)
        return if (scopes == mutedScopes) this else copy(chats = chats.withMutesResolved(scopes), mutedScopes = scopes)
    }

    private fun withFolders(update: TdApi.UpdateChatFolders): ChatCache =
        copy(
            folders = update.chatFolders.map { it.toChatFolder() }.toImmutableList(),
            mainChatListPosition = update.mainChatListPosition,
        )

    private fun withUnreadChatCount(update: TdApi.UpdateUnreadChatCount): ChatCache {
        val list = update.chatList.toChatListType() ?: return this
        return copy(unreadChatCounts = unreadChatCounts.putting(list, update.toUnreadChatCount()))
    }
}

/** The chats with [Chat.isMuted] resolved against [mutedScopes]; only those whose state changes are replaced. */
private fun PersistentMap<Long, CachedChat>.withMutesResolved(mutedScopes: Set<NotificationScope>) =
    mutate { changed ->
        for ((id, cached) in this) {
            val muted = resolveMute(cached.ownMute, cached.chat.type, mutedScopes)
            if (muted != cached.chat.isMuted) changed[id] = cached.copy(chat = cached.chat.copy(isMuted = muted))
        }
    }

/** How an update changes one chat; [apply] maps its content only for a chat in the cache. */
private class ChatChange(
    val chatId: Long,
    val apply: (Chat) -> Chat,
)

/** The change an update of a single chat's fields makes; null for any other update. */
private fun chatChange(update: TdApi.Update): ChatChange? =
    when (update) {
        is TdApi.UpdateChatTitle -> {
            ChatChange(update.chatId) { it.copy(title = update.title) }
        }

        is TdApi.UpdateChatPhoto -> {
            ChatChange(update.chatId) { it.copy(photo = update.photo?.toProfilePhoto()) }
        }

        is TdApi.UpdateChatPosition -> {
            ChatChange(update.chatId) { it.withPosition(update.position) }
        }

        // The last message and the draft change the chat's order, so their updates carry every position.
        is TdApi.UpdateChatLastMessage -> {
            ChatChange(update.chatId) {
                it.copy(lastMessage = update.lastMessage?.toMessage(), positions = update.positions.toChatPositions())
            }
        }

        is TdApi.UpdateChatDraftMessage -> {
            ChatChange(update.chatId) {
                it.copy(draft = update.draftMessage?.toChatDraft(), positions = update.positions.toChatPositions())
            }
        }

        is TdApi.UpdateChatReadInbox -> {
            ChatChange(update.chatId) {
                it.copy(lastReadInboxMessageId = update.lastReadInboxMessageId, unreadCount = update.unreadCount)
            }
        }

        is TdApi.UpdateChatReadOutbox -> {
            ChatChange(update.chatId) { it.copy(lastReadOutboxMessageId = update.lastReadOutboxMessageId) }
        }

        is TdApi.UpdateChatUnreadMentionCount -> {
            ChatChange(update.chatId) { it.copy(unreadMentionCount = update.unreadMentionCount) }
        }

        is TdApi.UpdateMessageMentionRead -> {
            ChatChange(update.chatId) { it.copy(unreadMentionCount = update.unreadMentionCount) }
        }

        is TdApi.UpdateChatUnreadReactionCount -> {
            ChatChange(update.chatId) { it.copy(unreadReactionCount = update.unreadReactionCount) }
        }

        is TdApi.UpdateMessageUnreadReactions -> {
            ChatChange(update.chatId) { it.copy(unreadReactionCount = update.unreadReactionCount) }
        }

        is TdApi.UpdateChatIsMarkedAsUnread -> {
            ChatChange(update.chatId) { it.copy(isMarkedAsUnread = update.isMarkedAsUnread) }
        }

        is TdApi.UpdateChatHasProtectedContent -> {
            ChatChange(update.chatId) { it.copy(hasProtectedContent = update.hasProtectedContent) }
        }

        is TdApi.UpdateChatMessageAutoDeleteTime -> {
            ChatChange(update.chatId) { it.copy(messageAutoDeleteTime = update.messageAutoDeleteTime) }
        }

        is TdApi.UpdateChatPermissions -> {
            ChatChange(update.chatId) { it.copy(permissions = update.permissions.toChatPermissions()) }
        }

        is TdApi.UpdateChatMessageSender -> {
            ChatChange(update.chatId) { it.copy(messageSender = update.messageSenderId?.toMessageSender()) }
        }

        else -> {
            null
        }
    }

/**
 * What marking the chat as read from the chat list takes, as the official apps do it; nothing for a chat with nothing
 * unread.
 * - `viewMessages` of the last message from the chat list with `force_read`, which reads every message up to it even
 *   though the chat is not open, and also removes the chat's unread mark (`MessagesManager::view_messages`).
 * - `readAllChatMentions` and `readAllChatReactions`, as viewing reads only the last message's own.
 * - `toggleChatIsMarkedAsUnread(false)` for a marked chat without a last message to view.
 */
private fun Chat.markAsReadRequests(): List<TdApi.Function<TdApi.Ok>> =
    buildList {
        val last = lastMessage
        if (last != null && (unreadCount > 0 || isMarkedAsUnread)) {
            add(TdApi.ViewMessages(id, longArrayOf(last.id), TdApi.MessageSourceChatList(), true))
        }
        if (unreadMentionCount > 0) add(TdApi.ReadAllChatMentions(id))
        if (unreadReactionCount > 0) add(TdApi.ReadAllChatReactions(id))
        if (last == null && isMarkedAsUnread) add(TdApi.ToggleChatIsMarkedAsUnread(id, false))
    }

/** The chat with [position] instead of its position in that list; order 0 takes the chat out of the list. */
private fun Chat.withPosition(position: TdApi.ChatPosition): Chat {
    val newPosition = position.toChatPosition() ?: return this
    val others = positions.filter { it.list != newPosition.list }
    return copy(positions = (if (newPosition.order == 0L) others else others + newPosition).toImmutableList())
}

/** The chats in [list], sorted as TDLib requires. */
private fun Map<Long, CachedChat>.sortedIn(list: ChatListType): ImmutableList<Chat> =
    values
        .mapNotNull { cached -> cached.chat.positionIn(list)?.let { ListedChat(it.order, cached.chat) } }
        .sortedWith(TDLIB_ORDER)
        .map { it.chat }
        .toImmutableList()

private class ListedChat(
    val order: Long,
    val chat: Chat,
)

/** By (order, chat id), both descending (td_api.tl `chatPosition`); compares primitives, so nothing is boxed. */
private val TDLIB_ORDER =
    Comparator<ListedChat> { a, b ->
        if (a.order != b.order) b.order.compareTo(a.order) else b.chat.id.compareTo(a.chat.id)
    }
