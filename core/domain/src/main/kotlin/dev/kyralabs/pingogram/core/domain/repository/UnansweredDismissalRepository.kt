package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.Flow

/**
 * The unanswered chats the user dismissed (`REQUIREMENTS.md` FR-X22): by chat id, the last message the chat had then.
 * A chat stays off the Unanswered page while that is still its last message, and comes back with a newer one. Kept on
 * this phone for the account, never in backups; logging out forgets them.
 */
interface UnansweredDismissalRepository {
    /** The dismissals, then every change. */
    val dismissed: Flow<ImmutableMap<Long, Long>>

    /** Dismisses chat [chatId] while [messageId] is its last message; fails with `DomainError.Unknown` if not saved. */
    suspend fun dismiss(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit>
}
