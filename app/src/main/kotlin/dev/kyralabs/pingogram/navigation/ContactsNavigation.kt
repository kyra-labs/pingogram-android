package dev.kyralabs.pingogram.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dev.kyralabs.pingogram.feature.contacts.navigation.contactScreens
import dev.kyralabs.pingogram.feature.contacts.navigation.contactsScreen
import dev.kyralabs.pingogram.feature.contacts.navigation.navigateToEditContact
import dev.kyralabs.pingogram.feature.contacts.navigation.navigateToNewContact

/**
 * The Contacts tab, with a contact's chat, Add contact and Edit contact pushed above it. Add and Edit contact go back
 * to where they were opened from once done: the contacts list, New chat, or a profile.
 */
internal fun NavGraphBuilder.contacts(
    navController: NavHostController,
    openChat: (chatId: Long) -> Unit,
) {
    contactsScreen(
        onOpenChat = openChat,
        onAddContact = { navController.navigateToNewContact() },
        onEditContact = { userId -> navController.navigateToEditContact(userId) },
    )
    contactScreens(onBack = { navController.popBackStack() })
}
