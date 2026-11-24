package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Whether links lose their tracking parameters when sent and when opened (Privacy and security → "Remove tracking from
 * links", `REQUIREMENTS.md` FR-X8, [dev.kyralabs.pingogram.core.domain.link.LinkCleaner]), kept on this device and on
 * by default.
 */
interface CleanLinksRepository {
    /** Whether it is on, then every change. Never fails: unreadable settings read as on. */
    val enabled: Flow<Boolean>

    /** Saves it; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setEnabled(enabled: Boolean): Outcome<Unit>
}
