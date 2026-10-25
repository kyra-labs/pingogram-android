package dev.kyralabs.pingogram.feature.chat.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.ScheduledMessagesRoute
import dev.kyralabs.pingogram.feature.chat.ui.scheduled.ScheduledMessagesRoute as ScheduledMessagesScreenRoute

/**
 * Adds the list of a chat's scheduled messages, [ScheduledMessagesRoute] (`REQUIREMENTS.md` FR-C5), to the graph:
 * pushed above the conversation whose bar opens it, also from the Chats tab's detail pane, with the shared axis on X.
 * [onBack] leaves it.
 */
fun NavGraphBuilder.scheduledMessagesScreen(onBack: () -> Unit) {
    composable<ScheduledMessagesRoute> { ScheduledMessagesScreenRoute(onBack = onBack) }
}

/** Pushes the scheduled messages of chat [chatId], unless they are shown already. */
fun NavController.navigateToScheduledMessages(chatId: Long) {
    navigate(ScheduledMessagesRoute(chatId)) { launchSingleTop = true }
}
