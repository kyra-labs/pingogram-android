package dev.kyralabs.pingogram.feature.contacts.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.ContactsRoute
import dev.kyralabs.pingogram.core.navigation.EditContactRoute
import dev.kyralabs.pingogram.core.navigation.NewContactRoute
import dev.kyralabs.pingogram.core.navigation.BirthdaysRoute as BirthdaysDestination
import dev.kyralabs.pingogram.feature.contacts.ui.ContactsRoute as ContactsScreenRoute
import dev.kyralabs.pingogram.feature.contacts.ui.EditContactRoute as EditContactScreenRoute
import dev.kyralabs.pingogram.feature.contacts.ui.NewContactRoute as NewContactScreenRoute
import dev.kyralabs.pingogram.feature.contacts.ui.birthdays.BirthdaysRoute as BirthdaysScreenRoute

/**
 * Adds the Contacts tab, [ContactsRoute], to the graph: a contact calls [onOpenChat] with the id of its private chat,
 * "Add contact" [onAddContact] ([navigateToNewContact]), and a contact's Edit [onEditContact] with its user id
 * ([navigateToEditContact]); the top bar opens the user's own QR code ([onOpenMyQrCode]) and the QR code scanner
 * ([onScanQrCode]).
 */
fun NavGraphBuilder.contactsScreen(
    onOpenChat: (chatId: Long) -> Unit,
    onAddContact: () -> Unit,
    onEditContact: (userId: Long) -> Unit,
    onOpenMyQrCode: () -> Unit = {},
    onScanQrCode: () -> Unit = {},
) {
    composable<ContactsRoute> {
        ContactsScreenRoute(
            onOpenChat = onOpenChat,
            onAddContact = onAddContact,
            onEditContact = onEditContact,
            onOpenMyQrCode = onOpenMyQrCode,
            onScanQrCode = onScanQrCode,
        )
    }
}

/**
 * Adds the screens pushed above the Contacts tab, or above whatever opens them: Add contact, [NewContactRoute], Edit
 * contact, [EditContactRoute] (from the contacts list, or from a user's profile), and Birthdays,
 * [BirthdaysDestination], whose rows open their chat through [onOpenChat]. [onBack] leaves any of them; Add and Edit
 * contact leave the same way once done, the new or edited contact then showing where it was opened from.
 */
fun NavGraphBuilder.contactScreens(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit = {},
) {
    composable<BirthdaysDestination> { BirthdaysScreenRoute(onBack = onBack, onOpenChat = onOpenChat) }
    composable<NewContactRoute> { NewContactScreenRoute(onBack = onBack, onAdded = { onBack() }) }
    composable<EditContactRoute> { EditContactScreenRoute(onBack = onBack, onDone = onBack) }
}

/** Pushes Add contact, [NewContactRoute], unless it is on top already. */
fun NavController.navigateToNewContact() = navigate(NewContactRoute) { launchSingleTop = true }

/** Pushes Edit contact for user [userId], [EditContactRoute], unless it is on top already. */
fun NavController.navigateToEditContact(userId: Long) = navigate(EditContactRoute(userId)) { launchSingleTop = true }
