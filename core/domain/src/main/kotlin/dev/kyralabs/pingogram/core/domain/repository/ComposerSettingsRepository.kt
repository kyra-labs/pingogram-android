package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * How the composer takes a hardware keyboard (`docs/DESIGN_SYSTEM.md` §9, `docs/audit/accessibility.md` A2): whether
 * Ctrl+Enter sends a message, Enter then starting a new line, instead of Enter sending. Off by default; kept on this
 * device across logouts, like the Appearance settings. The on-screen keyboard's Enter always starts a new line.
 */
interface ComposerSettingsRepository {
    /** Whether Ctrl+Enter sends, then every change. Never fails: a setting that cannot be read counts as off. */
    val sendWithCtrlEnter: Flow<Boolean>

    /** Saves whether Ctrl+Enter sends; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setSendWithCtrlEnter(enabled: Boolean): Outcome<Unit>
}
