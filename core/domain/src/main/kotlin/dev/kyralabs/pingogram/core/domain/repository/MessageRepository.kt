package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
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

    /** Forgets the history of every chat nobody observes, for `ComponentCallbacks2.onTrimMemory`. */
    fun trimMemory()
}
