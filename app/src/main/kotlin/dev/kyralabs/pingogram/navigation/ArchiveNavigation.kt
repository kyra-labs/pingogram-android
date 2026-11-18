package dev.kyralabs.pingogram.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dev.kyralabs.pingogram.core.navigation.ArchiveSettingsRoute
import dev.kyralabs.pingogram.core.navigation.ChatFolderEditRoute
import dev.kyralabs.pingogram.core.navigation.ChatFoldersRoute
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.feature.chats.navigation.archiveScreen
import dev.kyralabs.pingogram.feature.chats.navigation.archiveSettingsScreen
import dev.kyralabs.pingogram.feature.chats.navigation.catchUpScreen
import dev.kyralabs.pingogram.feature.chats.navigation.mentionsScreen

/**
 * The archived chats, pushed above the Chats tab as a conversation is, and their settings, pushed above them, and
 * Catch-up and Mentions & replies; a chat opens with [openChat], a topic a forum's row names pushed as its chat would
 * be, and a mention or reply pushes its chat at the message.
 */
internal fun NavGraphBuilder.archive(
    navController: NavHostController,
    openChat: (chatId: Long) -> Unit,
) {
    archiveScreen(
        onBack = { navController.popBackStack() },
        onOpenChat = openChat,
        onOpenSettings = { navController.navigate(ArchiveSettingsRoute) { launchSingleTop = true } },
        onOpenTopic = navController::navigateToTopic,
    )
    archiveSettingsScreen(onBack = { navController.popBackStack() })
    // Catch-up, pushed above the Chats tab from its header's chip: a chat it opens is pushed above it.
    catchUpScreen(onBack = { navController.popBackStack() }, onOpenChat = openChat)
    // Mentions & replies, pushed above the Chats tab from its header: a message it opens is pushed above it.
    mentionsScreen(
        onBack = { navController.popBackStack() },
        onOpenMessage = { chatId, messageId ->
            navController.navigate(ChatRoute(chatId, messageId)) { launchSingleTop = true }
        },
    )
}

/**
 * A folder tab's "Edit folder": Settings' editor of folder [folderId], or the Chat folders page for "All chats"
 * (null), pushed above the Chats tab as Settings' pages are.
 */
internal fun NavHostController.navigateToFolderEditor(folderId: Int?) {
    val route = if (folderId == null) ChatFoldersRoute else ChatFolderEditRoute(folderId)
    navigate(route) { launchSingleTop = true }
}
