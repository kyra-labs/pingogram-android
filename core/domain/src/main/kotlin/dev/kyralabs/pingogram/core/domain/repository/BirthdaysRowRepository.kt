package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Whether the Contacts tab shows its "Birthdays" row while contacts have a birthday these days (Privacy and security →
 * Contacts → "Birthdays", `REQUIREMENTS.md` FR-X18), kept on this device and on by default. Reminders already set ring
 * either way.
 */
interface BirthdaysRowRepository {
    /** Whether it is on, then every change. Never fails: unreadable settings read as on. */
    val enabled: Flow<Boolean>

    /** Saves it; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setEnabled(enabled: Boolean): Outcome<Unit>
}
