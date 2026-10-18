package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The chats TDLib announced in this session, kept in memory (`docs/ARCHITECTURE.md`, Repositories), their lists,
 * the chat folders and the unread counts of each list (`REQUIREMENTS.md` FR-L1–L3). Everything is dropped when TDLib
 * closes (logout).
 */
interface ChatRepository {
    /**
     * The chats in [list], in TDLib's order (pinned first), then the list after every change. Holds only the chats
     * TDLib has loaded: [loadChats] loads more.
     */
    fun chatList(list: ChatListType): Flow<ImmutableList<Chat>>

    /**
     * Asks TDLib to load up to [limit] more chats of [list] (TDLib may load fewer); they reach [chatList]. True if
     * chats were loaded, false once the whole list is loaded. Load a short first page at startup (20), then pages of
     * [PAGE_SIZE] (`docs/PERFORMANCE.md` §2). A request that gets no answer in time fails with `DomainError.Network`.
     */
    suspend fun loadChats(
        list: ChatListType,
        limit: Int = PAGE_SIZE,
    ): Outcome<Boolean>

    /** The chat with [id], then every change; null while TDLib has not announced the chat (see [getChat]). */
    fun chat(id: Long): Flow<Chat?>

    /**
     * The chat with [id] from memory, else from TDLib: `DomainError.NotFound` for a chat it does not know. A request
     * that gets no answer in time fails with `DomainError.Network`.
     */
    suspend fun getChat(id: Long): Outcome<Chat>

    /** The user's chat folders in their order, then every change; empty without folders. */
    val folders: Flow<ImmutableList<ChatFolder>>

    /** How many chats of [list] are unread, then every change; [UnreadChatCount.None] until TDLib counted them. */
    fun unreadChatCount(list: ChatListType): Flow<UnreadChatCount>

    companion object {
        /** Chats per [loadChats] page after the first. */
        const val PAGE_SIZE = 50
    }
}
