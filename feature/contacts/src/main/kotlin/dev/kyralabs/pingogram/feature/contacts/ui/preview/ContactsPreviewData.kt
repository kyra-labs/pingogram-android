package dev.kyralabs.pingogram.feature.contacts.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.contacts.R
import dev.kyralabs.pingogram.feature.contacts.ui.ContactItem
import dev.kyralabs.pingogram.feature.contacts.ui.ContactsContent
import dev.kyralabs.pingogram.feature.contacts.ui.ContactsUiState
import dev.kyralabs.pingogram.feature.contacts.ui.DeleteContactDialog
import dev.kyralabs.pingogram.feature.contacts.ui.contactSections
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale

/**
 * Sample states of the Contacts tab, shared by the previews and the screenshot tests: contacts in sections (some
 * online, the others last seen at various times), a long list with the fast-scroll index, search results, no results,
 * loading, no contacts, an error, and the question before deleting.
 */
internal object ContactsPreviewData {
    private fun contact(
        id: Long,
        name: String,
        status: UiText = UiText.resource(R.string.contacts_status_recently),
        isOnline: Boolean = false,
    ) = ContactItem(userId = id, name = name, photo = null, isOnline = isOnline, status = status)

    private val online = UiText.resource(R.string.contacts_status_online)

    val contacts =
        persistentListOf(
            contact(2, "Ada Lovelace", online, isOnline = true),
            contact(11, "Alan Turing", UiText.plural(R.plurals.contacts_status_minutes, 5, 5)),
            contact(15, "Barbara Liskov", UiText.resource(R.string.contacts_status_today, "9:41 AM")),
            contact(4, "Grace Hopper", UiText.resource(R.string.contacts_status_yesterday, "6:12 PM")),
            contact(12, "Hedy Lamarr", UiText.resource(R.string.contacts_status_week)),
            contact(13, "Katherine Johnson"),
            contact(14, "Margaret Hamilton", online, isOnline = true),
            contact(16, "Radia Perlman", UiText.resource(R.string.contacts_status_date, "Mar 17")),
            contact(17, "شهرزاد", UiText.resource(R.string.contacts_status_month)),
            contact(18, "42 Club", UiText.resource(R.string.contacts_status_long_ago)),
        )

    val content =
        ContactsUiState(ContactsContent.Contacts(contactSections(contacts, Locale.US), showsIndex = false))

    /** Enough contacts, across the alphabet, for the fast-scroll index. */
    val long =
        ContactsUiState(
            ContactsContent.Contacts(
                contactSections(
                    ('A'..'Z')
                        .flatMapIndexed { index, letter ->
                            val id = FIRST_LONG_ID + index * 2
                            listOf(contact(id, "${letter}nna Example"), contact(id + 1, "${letter}ric Sample"))
                        }.toImmutableList(),
                    Locale.US,
                ),
                showsIndex = true,
            ),
        )

    val results =
        ContactsUiState(
            ContactsContent.Results(persistentListOf(contacts[0], contacts[1])),
            query = "a",
        )

    val noResults = ContactsUiState(ContactsContent.Results(persistentListOf()), query = "zebra")

    val loading = ContactsUiState(ContactsContent.Loading)

    val empty = ContactsUiState(ContactsContent.Empty)

    val error = ContactsUiState(ContactsContent.Error(DomainError.Network))

    val deleting = content.copy(deleteDialog = DeleteContactDialog(4, "Grace Hopper"))

    private const val FIRST_LONG_ID = 100L
}
