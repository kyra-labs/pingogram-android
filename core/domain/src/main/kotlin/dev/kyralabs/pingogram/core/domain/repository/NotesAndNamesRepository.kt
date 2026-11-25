package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Whether the user's own names and private notes for groups, channels, bots and people who are not contacts show
 * (Settings → Chats → "Notes and names on this phone", `REQUIREMENTS.md` FR-X20), kept on this device and on by
 * default. Off, Telegram's names show again and nothing the user wrote is deleted.
 */
interface NotesAndNamesRepository {
    /** Whether it is on, then every change. Never fails: unreadable settings read as on. */
    val enabled: Flow<Boolean>

    /** Saves it; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setEnabled(enabled: Boolean): Outcome<Unit>
}
