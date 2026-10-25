package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Whether profiles and info pages show the user's or chat's ID (Privacy and security → "Show IDs"), kept on this device
 * and off by default: for bot developers and support, the number Telegram's bot API and other apps know them by.
 */
interface IdVisibilityRepository {
    /** Whether IDs show, then every change. Never fails: unreadable settings read as hidden. */
    val shown: Flow<Boolean>

    /** Saves it; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setShown(shown: Boolean): Outcome<Unit>
}
