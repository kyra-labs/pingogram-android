package dev.kyralabs.pingogram.feature.chats.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.ArchiveRoute
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.core.ui.transition.ProvideNavAnimatedVisibilityScope
import dev.kyralabs.pingogram.feature.chats.ui.ArchivedChatsRoute
import dev.kyralabs.pingogram.feature.chats.ui.ChatListRoute
import dev.kyralabs.pingogram.feature.chats.ui.NewChatRoute
import dev.kyralabs.pingogram.feature.chats.ui.SearchRoute
import dev.kyralabs.pingogram.core.navigation.NewChatRoute as NewChatDestination
import dev.kyralabs.pingogram.core.navigation.SearchRoute as SearchDestination

/**
 * The chat list of the Chats tab, [ChatsRoute], whose destination `:app` lays out: the list pane of a list-detail
 * layout beside the conversation of [openChatId] on expanded windows, the whole screen otherwise
 * (`docs/ARCHITECTURE.md`, Navigation). Its archive row calls [onOpenArchive], its rows call [onOpenChat] with their
 * chat's id, and its "New chat" buttons call [onOpenNewChat]; [openChatId]'s row looks and reads as selected. The
 * "New chat" floating action button grows into the New chat screen ([newChatScreen]) while the destination provides
 * its animated visibility scope.
 */
@Composable
fun ChatListPane(
    onOpenArchive: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    onOpenNewChat: () -> Unit,
    modifier: Modifier = Modifier,
    openChatId: Long? = null,
) {
    ChatListRoute(
        onOpenArchive = onOpenArchive,
        onOpenChat = onOpenChat,
        onOpenNewChat = onOpenNewChat,
        modifier = modifier,
        openChatId = openChatId,
    )
}

/** Adds the archived chats, [ArchiveRoute], to the graph; [onBack] leaves them, and a row calls [onOpenChat]. */
fun NavGraphBuilder.archiveScreen(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
) {
    composable<ArchiveRoute> { ArchivedChatsRoute(onBack = onBack, onOpenChat = onOpenChat) }
}

/**
 * Adds the New chat screen, [NewChatDestination], to the graph, which the Chats tab's "New chat" button grows into (a
 * container transform, through the app's shared transition scope); [onBack] leaves it, and a contact calls
 * [onOpenChat] with the id of its private chat.
 */
fun NavGraphBuilder.newChatScreen(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
) {
    composable<NewChatDestination> {
        ProvideNavAnimatedVisibilityScope(this) { NewChatRoute(onBack = onBack, onOpenChat = onOpenChat) }
    }
}

/**
 * Adds global search, [SearchDestination], to the graph; [onBack] leaves it. A chat it found calls [onOpenChat] with
 * its id, a message it found [onOpenMessage] with its chat and its id.
 */
fun NavGraphBuilder.searchScreen(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
) {
    composable<SearchDestination> {
        SearchRoute(onBack = onBack, onOpenChat = onOpenChat, onOpenMessage = onOpenMessage)
    }
}
