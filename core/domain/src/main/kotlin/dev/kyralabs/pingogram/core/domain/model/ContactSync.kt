package dev.kyralabs.pingogram.core.domain.model

import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import java.time.Instant

/**
 * A contact of the phone's address book, as contacts sync reads it (`docs/adr/0011-contacts-sync.md`): its display
 * [name] and one of its phone numbers, international ([phoneNumber], E.164 with `+` where Android could tell the
 * country, else as entered, digits only). [key] tells the phone's contacts apart, one per number.
 *
 * `toString()` leaves the name and number out, so a contact can be logged.
 */
data class PhoneContact(
    val key: String,
    val name: String,
    val phoneNumber: String,
) {
    override fun toString() = "PhoneContact(key=$key)"
}

/** The phone's contacts as the last sync found them on Telegram. */
data class PhoneContacts(
    /** The Telegram users among the phone's contacts, by user id, now in the user's Telegram contacts. */
    val onTelegram: ImmutableMap<Long, PhoneContact>,
    /** The phone's contacts no one uses Telegram with, sorted by name: the people the user can invite. */
    val notOnTelegram: ImmutableList<PhoneContact>,
) {
    override fun toString() = "PhoneContacts(${onTelegram.size} on Telegram, ${notOnTelegram.size} not)"
}

/** Contacts sync's settings on this device (`ContactSyncSettingsRepository`). */
data class ContactSyncSettings(
    /** The user turned contacts sync on. */
    val enabled: Boolean = false,
    /** When the phone's contacts were last sent to Telegram; null before the first time. */
    val lastSyncedAt: Instant? = null,
    /** The user dismissed the Contacts tab's offer to turn it on ("Not now"); Settings still offers it. */
    val promptDismissed: Boolean = false,
)

/** Where contacts sync is (`ContactSyncRepository.status`). */
sealed interface ContactSyncStatus {
    /** Off: nothing of the phone's contacts is read or sent. */
    data object Off : ContactSyncStatus

    /** On, but Android no longer lets Pingogram read the contacts (taken back in its settings): nothing syncs. */
    data object NeedsPermission : ContactSyncStatus

    /**
     * On and allowed: [isSyncing] while the contacts are on their way to Telegram; [lastSyncedAt] when they last
     * arrived; [error] what the last attempt failed with, until one succeeds.
     */
    data class On(
        val lastSyncedAt: Instant?,
        val isSyncing: Boolean = false,
        val error: DomainError? = null,
    ) : ContactSyncStatus
}
