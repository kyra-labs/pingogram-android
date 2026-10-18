package dev.kyralabs.pingogram.feature.chats.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.ArchiveRoute
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.core.navigation.SearchRoute
import dev.kyralabs.pingogram.feature.chats.ui.ArchivedChatsRoute
import dev.kyralabs.pingogram.feature.chats.ui.ChatListRoute
import dev.kyralabs.pingogram.feature.chats.ui.SearchScreen

/** Adds the Chats tab, [ChatsRoute]: the chat list, whose archive row calls [onOpenArchive]. */
fun NavGraphBuilder.chatsScreen(onOpenArchive: () -> Unit) {
    composable<ChatsRoute> { ChatListRoute(onOpenArchive = onOpenArchive) }
}

/** Adds the archived chats, [ArchiveRoute], to the graph; [onBack] leaves them. */
fun NavGraphBuilder.archiveScreen(onBack: () -> Unit) {
    composable<ArchiveRoute> { ArchivedChatsRoute(onBack = onBack) }
}

/** Adds global search, [SearchRoute], to the graph; [onBack] leaves it. */
fun NavGraphBuilder.searchScreen(onBack: () -> Unit) {
    composable<SearchRoute> { SearchScreen(onBack) }
}
