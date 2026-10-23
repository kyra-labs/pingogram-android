package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ContactSyncSettings
import dev.kyralabs.pingogram.core.domain.model.ContactSyncStatus
import dev.kyralabs.pingogram.core.domain.model.PhoneContacts
import kotlinx.coroutines.flow.Flow

/**
 * Contacts sync (`REQUIREMENTS.md` FR-P1, `docs/adr/0011-contacts-sync.md`), opt-in and off by default: the names and
 * phone numbers of the phone's contacts go to Telegram, which finds who among them uses Telegram and adds them to the
 * user's contacts, Telegram's own contact import. They are read and sent only when the user turns it on, when the
 * Contacts tab opens while it is on (at most once per app process, and not again within a while), and on "Sync now";
 * never in the background. Turning it off deletes what was imported from Telegram.
 *
 * Failures are `DomainError.Network`, `DomainError.FloodWait` and others as TDLib reports them, and
 * `DomainError.Unauthorized` without Android's permission to read the contacts.
 */
interface ContactSyncRepository {
    /** Where sync is, then every change; follows the permission as [refreshPermission] last found it. */
    val status: Flow<ContactSyncStatus>

    /** The settings, for the Contacts tab's offer ([ContactSyncSettings.promptDismissed]). */
    val settings: Flow<ContactSyncSettings>

    /**
     * The phone's contacts as found on Telegram, once sync is on and they were read in this process; null otherwise.
     */
    val phoneContacts: Flow<PhoneContacts?>

    /** Reads again whether Android lets Pingogram read the contacts, e.g. back from Android's settings. */
    fun refreshPermission()

    /** Turns sync on, once Android allowed reading the contacts, and syncs. */
    suspend fun turnOn(): Outcome<Unit>

    /**
     * Turns sync off: Telegram deletes the phone numbers it imported (contacts the user has stay contacts), and what
     * this device kept of sync goes. Stays on if Telegram cannot be told.
     */
    suspend fun turnOff(): Outcome<Unit>

    /**
     * What the Contacts tab does when it opens while sync is on: syncs, unless it did in this process or within a
     * while; then it only matches the phone's contacts with the Telegram contacts on the device, sending nothing.
     */
    suspend fun syncIfDue(): Outcome<Unit>

    /** "Sync now": syncs at once while sync is on. */
    suspend fun syncNow(): Outcome<Unit>

    /** "Not now" on the Contacts tab's offer to turn it on. */
    suspend fun dismissPrompt(): Outcome<Unit>
}
