package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Whether the app shows its experimental look (Settings → "Experimental UI", `docs/adr/0035-experimental-ui.md`), kept
 * on this device and off by default: a bolder Material 3 Expressive look for the chat list, search, chats, Contacts and
 * Settings home, beside the standard one, which stays as it is while this is off.
 */
interface ExperimentalUiRepository {
    /** Whether it is on, then every change. Never fails: unreadable settings read as off. */
    val enabled: Flow<Boolean>

    /** Saves it; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setEnabled(enabled: Boolean): Outcome<Unit>
}
