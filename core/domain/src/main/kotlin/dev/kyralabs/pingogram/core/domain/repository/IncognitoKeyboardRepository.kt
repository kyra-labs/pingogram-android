package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Whether Pingogram's text fields ask the keyboard not to learn from what is typed (Privacy and security → "Incognito
 * keyboard", `REQUIREMENTS.md` FR-X7), kept on this device and off by default.
 */
interface IncognitoKeyboardRepository {
    /** Whether it is on, then every change. Never fails: unreadable settings read as off. */
    val enabled: Flow<Boolean>

    /** Saves it; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setEnabled(enabled: Boolean): Outcome<Unit>
}
