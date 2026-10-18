package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.Message
import kotlinx.coroutines.flow.Flow

/**
 * The history of chats, loaded a page at a time as the conversation scrolls back (`REQUIREMENTS.md` FR-C1) and kept up
 * to date with TDLib's updates. Only the chats being observed and the most recently used others stay in memory
 * (`docs/PERFORMANCE.md` §3); everything is dropped when TDLib closes (logout).
 */
interface MessageRepository {
    /**
     * The loaded history of chat [chatId], then every change, for as long as it is collected. Observing loads nothing:
     * for a chat not loaded recently the first snapshot is empty without having reached the start, and [loadOlder]
     * loads the newest messages, so the caller sees every failure.
     */
    fun history(chatId: Long): Flow<ChatHistory>

    /**
     * Loads the messages before the oldest one in [history] (the newest messages if none is loaded) and succeeds once
     * they are there, or once there is nothing to load. A page can be short even when there are older messages; the
     * start is reached only when the server has none. Calls while a page of the same chat is loading share its
     * outcome. A failure (`DomainError.FloodWait`, `DomainError.Network`, `DomainError.NotFound`, …) leaves the
     * history as it was.
     */
    suspend fun loadOlder(chatId: Long): Outcome<Unit>

    /**
     * The message that message [messageId] of chat [chatId] replies to, for its reply quote when the replied message
     * is not in the loaded [history]: from memory when it was fetched before, else from TDLib, which may ask the
     * server. Replied messages of the same chat stay as up to date as the history while the chat is kept in memory.
     * Fails with `DomainError.NotFound` when the replied message was deleted, or [messageId] replies to nothing.
     */
    suspend fun repliedMessage(
        chatId: Long,
        messageId: Long,
    ): Outcome<Message>

    /** Forgets the history of every chat nobody observes, for `ComponentCallbacks2.onTrimMemory`. */
    fun trimMemory()
}
