package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * The ids of the chats locked on this device (`docs/adr/0029-locked-chats.md`), as they are stored: the settings file,
 * never backed up, not synced to the account's other apps. [ChatLockRepository] is what the app reads; this only keeps
 * the list.
 */
interface LockedChatIdsRepository {
    /** The stored ids, then every change. Never fails: an unreadable file reads as none. */
    val ids: Flow<Set<Long>>

    /** Replaces the stored ids with [ids]; fails with `DomainError.Unknown` if they cannot be written (a full disk). */
    suspend fun setIds(ids: Set<Long>): Outcome<Unit>
}
