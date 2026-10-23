package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The Contacts tab (`REQUIREMENTS.md` FR-P1): what its list shows ([content]), what the search field holds ([query]),
 * the contact the user is asked to confirm deleting ([deleteDialog]), if any, and where contacts sync is ([sync]).
 */
@Immutable
data class ContactsUiState(
    val content: ContactsContent = ContactsContent.Loading,
    val query: String = "",
    val deleteDialog: DeleteContactDialog? = null,
    val sync: ContactSyncUi = ContactSyncUi.Hidden,
)

/** What the Contacts tab shows of contacts sync (`docs/adr/0011-contacts-sync.md`) above the contacts. */
@Immutable
sealed interface ContactSyncUi {
    /** Off, and its offer dismissed: nothing (Settings still offers it). */
    data object Hidden : ContactSyncUi

    /** Off: a card offers to turn it on. */
    data object Offer : ContactSyncUi

    /** On, but Android no longer lets Pingogram read the contacts: a card asks again, or to turn it off. */
    data object NeedsPermission : ContactSyncUi

    /**
     * On: a row says so, [isSyncing] while the contacts are on their way, else when they last synced ([lastSynced],
     * null before the first time) or what the last sync failed with ([error]), with "Sync now".
     */
    data class On(
        val isSyncing: Boolean,
        val lastSynced: UiText?,
        val error: DomainError?,
    ) : ContactSyncUi
}

/** What the contacts list shows below "Add contact" and "Invite friends". */
@Immutable
sealed interface ContactsContent {
    /** The contacts are on their way. */
    data object Loading : ContactsContent

    /** The user has no contacts. */
    data object Empty : ContactsContent

    /** Listing the contacts failed with [error]: shown with a retry ([ContactsEvent.Retry]). */
    data class Error(
        val error: DomainError,
    ) : ContactsContent

    /**
     * Every contact, in alphabetical [sections]; [showsIndex] whether the list is long enough for the fast-scroll index
     * of their letters. With contacts sync on, the phone's contacts not on Telegram follow under "Invite to Telegram"
     * ([invites]).
     */
    data class Contacts(
        val sections: ImmutableList<ContactSection>,
        val showsIndex: Boolean,
        val invites: ImmutableList<InviteItem> = persistentListOf(),
    ) : ContactsContent {
        override fun toString() = "Contacts(${sections.size} sections, ${invites.size} invites)"
    }

    /** The contacts that match the search field's query, in the list's order; empty when none does. */
    data class Results(
        val contacts: ImmutableList<ContactItem>,
    ) : ContactsContent {
        override fun toString() = "Results(${contacts.size})"
    }
}

/**
 * The contacts whose names start with [letter] (`"#"` for names that start with no letter), under that letter's
 * sticky header.
 */
@Immutable
data class ContactSection(
    val letter: String,
    val contacts: ImmutableList<ContactItem>,
)

/**
 * A contact's row: the avatar of [photo] or the initials of [name] (the name the user gave the contact), the online
 * dot ([isOnline]) and the [status] below the name ("online", "last seen 5 minutes ago").
 */
@Immutable
data class ContactItem(
    val userId: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val isOnline: Boolean,
    val status: UiText,
    /** Contacts sync found them among the phone's contacts. */
    val fromPhone: Boolean = false,
) {
    override fun toString() = "ContactItem(userId=$userId, isOnline=$isOnline, fromPhone=$fromPhone)"
}

/**
 * A contact of the phone no one uses Telegram with, under "Invite to Telegram": its [name] and [phoneNumber]
 * (international), [key] keeping it apart from the others.
 */
@Immutable
data class InviteItem(
    val key: String,
    val name: String,
    val phoneNumber: String,
) {
    override fun toString() = "InviteItem(key=$key)"
}

/** The question before contact [userId], [name], is deleted. */
@Immutable
data class DeleteContactDialog(
    val userId: Long,
    val name: String,
) {
    override fun toString() = "DeleteContactDialog(userId=$userId)"
}

/** What the user does on the Contacts tab, sent to [ContactsViewModel.onEvent]. */
sealed interface ContactsEvent {
    /** The search field now holds [query]. */
    data class QueryChanged(
        val query: String,
    ) : ContactsEvent

    /** A contact was tapped: opens the private chat with them. */
    data class OpenChat(
        val userId: Long,
    ) : ContactsEvent

    /** "Delete" in a contact's menu: asks first ([ContactsUiState.deleteDialog]). */
    data class Delete(
        val userId: Long,
    ) : ContactsEvent

    /** The deletion was confirmed. */
    data object ConfirmDelete : ContactsEvent

    /** The deletion was not confirmed. */
    data object DismissDialog : ContactsEvent

    /** "Invite friends": shares an invitation to Telegram. */
    data object Invite : ContactsEvent

    /** "Try again" after listing the contacts failed. */
    data object Retry : ContactsEvent

    /** The tab shows (again): contacts sync reads the permission again, and syncs if it is due. */
    data object Started : ContactsEvent

    /** "Not now" on the offer of contacts sync. */
    data object DismissSyncOffer : ContactsEvent

    /** "Invite" for a phone's contact not on Telegram ([InviteItem]): a message with the invitation. */
    data class InvitePhoneContact(
        val phoneNumber: String,
    ) : ContactsEvent
}

/** One-off effects of [ContactsViewModel], handled by the screen once each. */
sealed interface ContactsEffect {
    /** Opens the conversation of chat [chatId]. */
    data class OpenChat(
        val chatId: Long,
    ) : ContactsEffect

    /** Opens the share sheet with an invitation to Telegram that carries [link]. */
    data class ShareInvite(
        val link: String,
    ) : ContactsEffect

    /**
     * Opens the messaging app with an invitation to Telegram carrying [link] for [phoneNumber], else the share sheet.
     */
    data class InviteByMessage(
        val phoneNumber: String,
        val link: String,
    ) : ContactsEffect {
        override fun toString() = "InviteByMessage(…)"
    }

    /** Contact [name] was deleted: a snackbar says so. */
    data class Deleted(
        val name: String,
    ) : ContactsEffect {
        override fun toString() = "Deleted(…)"
    }

    /** Something failed with [error]: a snackbar says so. */
    data class ShowError(
        val error: DomainError,
    ) : ContactsEffect
}
