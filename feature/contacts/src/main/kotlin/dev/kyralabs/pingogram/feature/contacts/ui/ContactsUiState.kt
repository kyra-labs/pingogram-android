package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList

/**
 * The Contacts tab (`REQUIREMENTS.md` FR-P1): what its list shows ([content]), what the search field holds ([query]),
 * and the contact the user is asked to confirm deleting ([deleteDialog]), if any.
 */
@Immutable
data class ContactsUiState(
    val content: ContactsContent = ContactsContent.Loading,
    val query: String = "",
    val deleteDialog: DeleteContactDialog? = null,
)

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
     * of their letters.
     */
    data class Contacts(
        val sections: ImmutableList<ContactSection>,
        val showsIndex: Boolean,
    ) : ContactsContent {
        override fun toString() = "Contacts(${sections.size} sections)"
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
) {
    override fun toString() = "ContactItem(userId=$userId, isOnline=$isOnline)"
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
