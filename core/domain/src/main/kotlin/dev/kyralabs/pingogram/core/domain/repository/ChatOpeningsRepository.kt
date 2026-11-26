package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * When the account's chats were last opened on this phone, for Cleanup suggestions (`REQUIREMENTS.md` FR-X24): kept on
 * the phone only, never in backups nor sent anywhere, and forgotten when the account logs out.
 */
interface ChatOpeningsRepository {
    /** The openings, read the first time they are asked for, then every change. */
    val openings: Flow<ChatOpenings>

    /** Counts chat [chatId] as opened now, as "Not now" does; fails with `DomainError.Unknown` if it is not saved. */
    suspend fun markOpened(chatId: Long): Outcome<Unit>
}

/** When each chat was last opened ([lastOpened]), kept [since] that moment: a chat not in it was not opened since. */
data class ChatOpenings(
    val since: Instant,
    val lastOpened: ImmutableMap<Long, Instant> = persistentMapOf(),
) {
    /** When chat [chatId] was last opened, or [since] for a chat not opened since. */
    fun lastOpenedOf(chatId: Long): Instant = lastOpened[chatId] ?: since
}
