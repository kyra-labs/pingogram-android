package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.PhoneContact

/**
 * The phone's address book, read only for contacts sync (`docs/adr/0011-contacts-sync.md`), with Android's
 * `READ_CONTACTS` permission: the names and phone numbers of its contacts, nothing else. Read when asked, never in the
 * background, and never kept.
 */
interface DeviceContactsRepository {
    /** Whether Android lets Pingogram read the contacts now; the user can take it back in Android's settings. */
    fun canRead(): Boolean

    /**
     * Every phone number of every contact, one [PhoneContact] each, numbers in international form where Android can
     * tell their country, without duplicates. Fails with `DomainError.Unauthorized` without the permission.
     */
    suspend fun read(): Outcome<List<PhoneContact>>
}
