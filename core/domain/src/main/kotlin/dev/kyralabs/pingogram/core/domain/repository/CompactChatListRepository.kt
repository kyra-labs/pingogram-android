package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Whether the chat list shows its rows compact, a smaller avatar on a lower row (Settings → Chats → Chat list →
 * "Compact chat list", `REQUIREMENTS.md` FR-X19), kept on this device and off by default.
 */
interface CompactChatListRepository {
    /** Whether it is on, then every change. Never fails: unreadable settings read as off. */
    val enabled: Flow<Boolean>

    /** Saves it; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setEnabled(enabled: Boolean): Outcome<Unit>
}
