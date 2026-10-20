package dev.kyralabs.pingogram.feature.contacts.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.ContactsRoute
import dev.kyralabs.pingogram.feature.contacts.ui.ContactsScreen

/** Adds the Contacts tab, [ContactsRoute], to the graph; its "New chat" button calls [onOpenNewChat]. */
fun NavGraphBuilder.contactsScreen(onOpenNewChat: () -> Unit) {
    composable<ContactsRoute> { ContactsScreen(onOpenNewChat = onOpenNewChat) }
}
