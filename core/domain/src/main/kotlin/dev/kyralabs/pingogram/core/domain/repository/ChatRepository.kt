package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.DeleteChatOptions
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The chats TDLib announced in this session, kept in memory (`docs/ARCHITECTURE.md`, Repositories), their lists,
 * the chat folders and the unread counts of each list (`REQUIREMENTS.md` FR-L1–L3), and what the chat list does to a
 * chat (FR-L4). Everything is dropped when TDLib closes (logout).
 *
 * The actions on a chat succeed once TDLib accepted them; their effect reaches [chatList] and [chat] through TDLib's
 * updates, which TDLib sends at once, before it tells Telegram. They fail with a `DomainError`: `FloodWait`, `Network`
 * for a request that gets no answer in time, `NotFound` for a chat TDLib does not know, …
 *
 * One function per action of the chat list, hence `TooManyFunctions` is suppressed: they act on the chats this
 * repository keeps.
 */
@Suppress("TooManyFunctions")
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

    /**
     * Where the main list ("All chats") goes among the tabs of the [folders]: 0 before the first folder, up to the
     * number of folders after the last one (td_api.tl `updateChatFolders.main_chat_list_position`). Then every change;
     * 0 until TDLib reports the folders. Only Telegram Premium users can move it.
     */
    val mainChatListPosition: Flow<Int>

    /** How many chats of [list] are unread, then every change; [UnreadChatCount.None] until TDLib counted them. */
    fun unreadChatCount(list: ChatListType): Flow<UnreadChatCount>

    /**
     * Pins chat [chatId] at the top of [list], or unpins it. Fails with `DomainError.TooManyPinnedChats` when the list
     * has as many pinned chats as allowed.
     */
    suspend fun setPinned(
        chatId: Long,
        list: ChatListType,
        isPinned: Boolean,
    ): Outcome<Unit>

    /**
     * Mutes chat [chatId] forever, or unmutes it, whatever the default for its kind of chat; its other notification
     * settings (sound, preview, …) stay. The Saved Messages chat cannot be muted.
     */
    suspend fun setMuted(
        chatId: Long,
        isMuted: Boolean,
    ): Outcome<Unit>

    /** Moves chat [chatId] to the archive, or back to the main list. */
    suspend fun setArchived(
        chatId: Long,
        isArchived: Boolean,
    ): Outcome<Unit>

    /**
     * Marks everything in chat [chatId] as read, as opening it would: its messages, mentions and reactions, and a mark
     * set by [markAsUnread].
     */
    suspend fun markAsRead(chatId: Long): Outcome<Unit>

    /** Marks chat [chatId] as unread, without changing its messages; reading it ([markAsRead]) removes the mark. */
    suspend fun markAsUnread(chatId: Long): Outcome<Unit>

    /** What deleting chat [chatId] would do and allow, read from TDLib when asked; see [deleteChat]. */
    suspend fun deleteOptions(chatId: Long): Outcome<DeleteChatOptions>

    /**
     * Removes chat [chatId] from the chat lists as [deleteOptions] describes it: deletes the conversation, or leaves
     * the group or channel. With [forEveryone], where [DeleteChatOptions.canDeleteForEveryone] allows it, the chat is
     * deleted for everyone instead; elsewhere [forEveryone] is ignored. Takes several requests for some chats; stops
     * at the first that fails.
     */
    suspend fun deleteChat(
        chatId: Long,
        forEveryone: Boolean,
    ): Outcome<Unit>

    companion object {
        /** Chats per [loadChats] page after the first. */
        const val PAGE_SIZE = 50
    }
}
