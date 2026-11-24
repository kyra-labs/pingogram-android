package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Whether adding a contact shares the user's phone number with them unless changed on the form (Privacy and security →
 * Contacts → "Share my phone number with new contacts", `REQUIREMENTS.md` FR-X9), kept on this device and off by
 * default.
 */
interface SharePhoneNumberRepository {
    /** Whether it is on, then every change. Never fails: unreadable settings read as off. */
    val enabled: Flow<Boolean>

    /** Saves it; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setEnabled(enabled: Boolean): Outcome<Unit>
}
