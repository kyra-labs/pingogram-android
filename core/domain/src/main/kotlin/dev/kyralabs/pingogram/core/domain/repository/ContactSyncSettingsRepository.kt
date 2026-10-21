package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ContactSyncSettings
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Contacts sync's settings (`docs/adr/0011-contacts-sync.md`), kept on this device for the logged-in account: whether
 * it is on (off by default), when it last synced, and whether the Contacts tab's offer was dismissed. Cleared when the
 * user turns sync off and at logout. Writes fail with `DomainError.Unknown` if the file cannot be written (a full
 * disk); a file that cannot be read counts as the defaults.
 */
interface ContactSyncSettingsRepository {
    /** The settings, then every change. */
    val settings: Flow<ContactSyncSettings>

    suspend fun setEnabled(enabled: Boolean): Outcome<Unit>

    /** When the contacts last synced; null forgets it. */
    suspend fun setLastSyncedAt(time: Instant?): Outcome<Unit>

    suspend fun setPromptDismissed(dismissed: Boolean): Outcome<Unit>

    /** Back to the defaults: off, never synced, the offer not dismissed. */
    suspend fun clear(): Outcome<Unit>
}
