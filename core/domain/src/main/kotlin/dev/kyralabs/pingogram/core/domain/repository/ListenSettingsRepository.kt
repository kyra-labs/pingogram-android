package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Whether messages can be listened to (Settings → Chats → "Listen to messages", `REQUIREMENTS.md` FR-X26): "Listen"
 * in the message menu and "Listen to unread posts" in channels, read aloud by an offline voice of the device. Kept on
 * this device, on by default.
 */
interface ListenSettingsRepository {
    /** Whether it is on, then every change. Never fails: unreadable settings read as on. */
    val enabled: Flow<Boolean>

    /** Saves it; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setEnabled(enabled: Boolean): Outcome<Unit>
}
