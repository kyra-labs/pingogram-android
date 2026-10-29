package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Whether phone numbers show on screen (Privacy and security → "Hide phone numbers"), kept on this device and off by
 * default: for sharing the screen or streaming, every screen that shows a number shows its calling code only.
 */
interface PhoneNumberVisibilityRepository {
    /** Whether numbers are hidden, then every change. Never fails: unreadable settings read as shown. */
    val hidden: Flow<Boolean>

    /** Saves it; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setHidden(hidden: Boolean): Outcome<Unit>
}
