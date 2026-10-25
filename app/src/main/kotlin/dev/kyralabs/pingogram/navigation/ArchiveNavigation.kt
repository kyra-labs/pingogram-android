package dev.kyralabs.pingogram.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dev.kyralabs.pingogram.core.navigation.ArchiveSettingsRoute
import dev.kyralabs.pingogram.core.navigation.ChatFolderEditRoute
import dev.kyralabs.pingogram.core.navigation.ChatFoldersRoute
import dev.kyralabs.pingogram.feature.chats.navigation.archiveScreen
import dev.kyralabs.pingogram.feature.chats.navigation.archiveSettingsScreen

/**
 * The archived chats, pushed above the Chats tab as a conversation is, and their settings, pushed above them; a chat
 * opens with [openChat].
 */
internal fun NavGraphBuilder.archive(
    navController: NavHostController,
    openChat: (chatId: Long) -> Unit,
) {
    archiveScreen(
        onBack = { navController.popBackStack() },
        onOpenChat = openChat,
        onOpenSettings = { navController.navigate(ArchiveSettingsRoute) { launchSingleTop = true } },
    )
    archiveSettingsScreen(onBack = { navController.popBackStack() })
}

/**
 * A folder tab's "Edit folder": Settings' editor of folder [folderId], or the Chat folders page for "All chats"
 * (null), pushed above the Chats tab as Settings' pages are.
 */
internal fun NavHostController.navigateToFolderEditor(folderId: Int?) {
    val route = if (folderId == null) ChatFoldersRoute else ChatFolderEditRoute(folderId)
    navigate(route) { launchSingleTop = true }
}
