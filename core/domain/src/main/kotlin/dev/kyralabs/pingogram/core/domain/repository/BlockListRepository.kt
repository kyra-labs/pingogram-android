package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BlockedSendersPage
import dev.kyralabs.pingogram.core.domain.model.MessageSender

/**
 * The users and chats the current user blocked (`REQUIREMENTS.md` FR-S1, Privacy and security): TDLib's main block
 * list, which stops them from writing to the user and from seeing their status and photos. Nothing is cached: each
 * call asks TDLib. A request that gets no answer in time fails with `DomainError.Network`.
 */
interface BlockListRepository {
    /** Up to [limit] (at most 100, TDLib's limit) blocked users and chats, after the first [offset] of them. */
    suspend fun blockedSenders(
        offset: Int,
        limit: Int = PAGE_SIZE,
    ): Outcome<BlockedSendersPage>

    /** Takes [sender] off the block list. */
    suspend fun unblock(sender: MessageSender): Outcome<Unit>

    /** Puts [sender] on the block list, e.g. to undo [unblock]. */
    suspend fun block(sender: MessageSender): Outcome<Unit>

    companion object {
        /** The users and chats a page holds, as many as the chat list's. */
        const val PAGE_SIZE = 50
    }
}
