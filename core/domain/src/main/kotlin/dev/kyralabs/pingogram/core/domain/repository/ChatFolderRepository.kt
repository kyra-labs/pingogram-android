package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatFolderInvite
import dev.kyralabs.pingogram.core.domain.model.ChatFolderInviteLink
import dev.kyralabs.pingogram.core.domain.model.ChatFolderSettings
import dev.kyralabs.pingogram.core.domain.model.RecommendedChatFolder

/**
 * Creating and editing chat folders (`REQUIREMENTS.md` FR-L3, Settings → Chat folders). The folders themselves, their
 * order and the main list's place among them come from `ChatRepository.folders`, which follows every change made here
 * or on another device. Telegram's refusals: `DomainError.ChatFolderEmpty` for a folder that includes nothing,
 * `DomainError.TooManyChatFolders` past the account's limit (10, or 20 with Telegram Premium), and
 * `DomainError.InviteLinkInvalid` for a folder invite link that no longer works.
 */
@Suppress("TooManyFunctions") // One function per TDLib request of chat folders.
interface ChatFolderRepository {
    /** What folder [id] holds. */
    suspend fun folder(id: Int): Outcome<ChatFolderSettings>

    /** Creates a folder with [settings]; answers with its id. */
    suspend fun createFolder(settings: ChatFolderSettings): Outcome<Int>

    /** Replaces what folder [id] holds with [settings]. */
    suspend fun editFolder(
        id: Int,
        settings: ChatFolderSettings,
    ): Outcome<Unit>

    /**
     * The chats the user could leave with shared folder [id] (`getChatFolderChatsToLeave`): those joined through its
     * links that are in no other folder.
     */
    suspend fun chatsToLeave(id: Int): Outcome<List<Long>>

    /** Deletes folder [id], leaving [leaveChatIds] too (from [chatsToLeave]; empty to stay in every chat). */
    suspend fun deleteFolder(
        id: Int,
        leaveChatIds: List<Long> = emptyList(),
    ): Outcome<Unit>

    /**
     * Puts the folders in the order of [folderIds] (every folder, once), the main list at [mainChatListPosition] among
     * them (0 before the first folder); only Telegram Premium users can move the main list.
     */
    suspend fun reorderFolders(
        folderIds: List<Int>,
        mainChatListPosition: Int,
    ): Outcome<Unit>

    /** The folders Telegram suggests from the user's chats. */
    suspend fun recommendedFolders(): Outcome<List<RecommendedChatFolder>>

    /** The chats of folder [id] that an invite link can share: groups and channels the user can invite to. */
    suspend fun shareableChats(id: Int): Outcome<List<Long>>

    /** The invite links the user made for folder [id]. */
    suspend fun inviteLinks(id: Int): Outcome<List<ChatFolderInviteLink>>

    /** Makes an invite link for folder [id] that shares [chatIds] (from [shareableChats]); the folder becomes shared. */
    suspend fun createInviteLink(
        id: Int,
        chatIds: List<Long>,
    ): Outcome<ChatFolderInviteLink>

    /** Deletes [link] of folder [id]; the chats joined through it stay. */
    suspend fun deleteInviteLink(
        id: Int,
        link: String,
    ): Outcome<Unit>

    /** What the folder invite [link] (`t.me/addlist/…`) offers, without adding anything. */
    suspend fun checkInviteLink(link: String): Outcome<ChatFolderInvite>

    /** Adds the folder of invite [link], joining [chatIds] of its missing chats. */
    suspend fun addFolderByInviteLink(
        link: String,
        chatIds: List<Long>,
    ): Outcome<Unit>
}
