package dev.kyralabs.pingogram.feature.chats.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.ArchiveRoute
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.core.navigation.SearchRoute
import dev.kyralabs.pingogram.feature.chats.ui.ArchivedChatsRoute
import dev.kyralabs.pingogram.feature.chats.ui.ChatListRoute
import dev.kyralabs.pingogram.feature.chats.ui.SearchScreen

/**
 * The chat list of the Chats tab, [ChatsRoute], whose destination `:app` lays out: the list pane of a list-detail
 * layout beside the conversation of [openChatId] on expanded windows, the whole screen otherwise
 * (`docs/ARCHITECTURE.md`, Navigation). Its archive row calls [onOpenArchive], and its rows call [onOpenChat] with
 * their chat's id; [openChatId]'s row looks and reads as selected.
 */
@Composable
fun ChatListPane(
    onOpenArchive: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
    openChatId: Long? = null,
) {
    ChatListRoute(onOpenArchive = onOpenArchive, onOpenChat = onOpenChat, modifier = modifier, openChatId = openChatId)
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
