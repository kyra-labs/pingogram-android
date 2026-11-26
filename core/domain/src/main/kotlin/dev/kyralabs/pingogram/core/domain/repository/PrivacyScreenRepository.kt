package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Whether the privacy screen is on (Privacy and security → "Privacy screen", and its Quick Settings tile,
 * `REQUIREMENTS.md` FR-X25): message text and photos stay blurred until tapped. Kept on this device, off by default,
 * and on until turned off.
 */
interface PrivacyScreenRepository {
    /** Whether it is on, then every change. Never fails: unreadable settings read as off. */
    val enabled: Flow<Boolean>

    /** Saves it; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setEnabled(enabled: Boolean): Outcome<Unit>
}
