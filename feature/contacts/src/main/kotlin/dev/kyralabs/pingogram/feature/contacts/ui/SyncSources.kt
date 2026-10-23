package dev.kyralabs.pingogram.feature.contacts.ui

import dev.kyralabs.pingogram.core.domain.model.ContactSyncSettings
import dev.kyralabs.pingogram.core.domain.model.ContactSyncStatus
import dev.kyralabs.pingogram.core.domain.model.PhoneContacts
import java.time.Instant

// How ContactsViewModel maps contacts sync (docs/adr/0011-contacts-sync.md) to the tab.

/** What the tab shows of contacts sync: its [status], its [settings] and the [phoneContacts] it found. */
internal class SyncSources(
    val status: ContactSyncStatus,
    val settings: ContactSyncSettings,
    val phoneContacts: PhoneContacts?,
)

/** The offer, the paused card or the status row, the last sync's time from [formatter] as of [now]. */
internal fun SyncSources.toUi(
    formatter: ContactStatusFormatter,
    now: Instant,
): ContactSyncUi =
    when (status) {
        ContactSyncStatus.Off -> {
            if (settings.promptDismissed) ContactSyncUi.Hidden else ContactSyncUi.Offer
        }

        ContactSyncStatus.NeedsPermission -> {
            ContactSyncUi.NeedsPermission
        }

        is ContactSyncStatus.On -> {
            ContactSyncUi.On(
                isSyncing = status.isSyncing,
                lastSynced = status.lastSyncedAt?.let { formatter.lastSynced(it, now) },
                error = status.error,
            )
        }
    }

/** The phone's contacts not on Telegram, as "Invite to Telegram" rows. */
internal fun PhoneContacts.toInvites(): List<InviteItem> =
    notOnTelegram.map { InviteItem(key = it.key, name = it.name, phoneNumber = it.phoneNumber) }
