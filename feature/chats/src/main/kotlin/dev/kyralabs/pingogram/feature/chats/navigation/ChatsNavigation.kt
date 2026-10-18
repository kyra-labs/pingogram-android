package dev.kyralabs.pingogram.feature.chats.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.ArchiveRoute
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.core.navigation.SearchRoute
import dev.kyralabs.pingogram.feature.chats.ui.ArchivedChatsRoute
import dev.kyralabs.pingogram.feature.chats.ui.ChatListRoute
import dev.kyralabs.pingogram.feature.chats.ui.SearchScreen

/**
 * Adds the Chats tab, [ChatsRoute]: the chat list, whose archive row calls [onOpenArchive] and whose rows call
 * [onOpenChat] with their chat's id.
 */
fun NavGraphBuilder.chatsScreen(
    onOpenArchive: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
) {
    composable<ChatsRoute> { ChatListRoute(onOpenArchive = onOpenArchive, onOpenChat = onOpenChat) }
}

/** Adds the archived chats, [ArchiveRoute], to the graph; [onBack] leaves them, and a row calls [onOpenChat]. */
fun NavGraphBuilder.archiveScreen(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
) {
    composable<ArchiveRoute> { ArchivedChatsRoute(onBack = onBack, onOpenChat = onOpenChat) }
}

/** Adds global search, [SearchRoute], to the graph; [onBack] leaves it. */
fun NavGraphBuilder.searchScreen(onBack: () -> Unit) {
    composable<SearchRoute> { SearchScreen(onBack) }
}
