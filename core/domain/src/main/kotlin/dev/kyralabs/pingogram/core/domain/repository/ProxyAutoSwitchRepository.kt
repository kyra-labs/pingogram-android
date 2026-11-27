package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Whether Pingogram switches to a working proxy when the one in use fails (Settings → Data and storage → Proxy →
 * "Switch to a working proxy", `REQUIREMENTS.md` FR-X27): after about 15 seconds of connecting through it, the quickest
 * of the user's other proxies that answers, on every account. Kept on this device, off by default.
 */
interface ProxyAutoSwitchRepository {
    /** Whether it is on, then every change. Never fails: unreadable settings read as off. */
    val enabled: Flow<Boolean>

    /** Saves it; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setEnabled(enabled: Boolean): Outcome<Unit>
}
