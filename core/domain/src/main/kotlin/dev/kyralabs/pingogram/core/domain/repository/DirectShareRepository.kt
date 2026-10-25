package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Whether Android's share sheet offers Pingogram's chats as Direct Share targets (Privacy and security → "Chats in the
 * share sheet", `REQUIREMENTS.md` FR-N2), kept on this device and on by default. Off, no conversation shortcut is a
 * share target: the share sheet lists Pingogram itself, whose own chat picker still takes the share.
 */
interface DirectShareRepository {
    /** Whether chats are offered, then every change. Never fails: unreadable settings read as offered. */
    val enabled: Flow<Boolean>

    /** Saves it; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setEnabled(enabled: Boolean): Outcome<Unit>
}
