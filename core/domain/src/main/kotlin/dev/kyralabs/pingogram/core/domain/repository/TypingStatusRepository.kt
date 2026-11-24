package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Whether Pingogram tells a chat what the user is doing in it (Privacy and security → "Send typing status"): typing,
 * recording a voice or round video message, uploading a photo or a file. Kept on this device, on by default; while
 * it is off nothing is sent, not even the end of an action.
 */
interface TypingStatusRepository {
    /** Whether it is on, then every change. Never fails: unreadable settings read as on. */
    val enabled: Flow<Boolean>

    /** Saves it; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setEnabled(enabled: Boolean): Outcome<Unit>
}
