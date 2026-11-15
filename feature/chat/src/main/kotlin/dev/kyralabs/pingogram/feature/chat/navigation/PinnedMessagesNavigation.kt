package dev.kyralabs.pingogram.feature.chat.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.PinnedMessagesRoute
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedMessagesRoute as PinnedMessagesScreenRoute

/**
 * Adds the page of a conversation's pinned messages, [PinnedMessagesRoute] (`docs/DESIGN_SYSTEM.md` §6 "Pinned messages
 * bar"), to the graph: pushed above the conversation whose bar opens it, also from the Chats tab's detail pane, with
 * the
 * shared axis on X. [onBack] leaves it, also after a jump to a message.
 */
fun NavGraphBuilder.pinnedMessagesScreen(onBack: () -> Unit) {
    composable<PinnedMessagesRoute> { PinnedMessagesScreenRoute(onBack = onBack) }
}

/** Pushes the page [route] of a conversation's pinned messages, unless it is shown already. */
fun NavController.navigateToPinnedMessages(route: PinnedMessagesRoute) {
    navigate(route) { launchSingleTop = true }
}
