package dev.kyralabs.pingogram.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.ArchiveRoute
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.core.navigation.ForwardRoute
import dev.kyralabs.pingogram.core.navigation.NewChatRoute
import dev.kyralabs.pingogram.core.ui.transition.ProvideNavAnimatedVisibilityScope
import dev.kyralabs.pingogram.feature.chat.navigation.navigateToScheduledMessages
import dev.kyralabs.pingogram.feature.chats.navigation.ChatListHeaderActions
import dev.kyralabs.pingogram.feature.chats.navigation.GlobalSearchState
import dev.kyralabs.pingogram.feature.contacts.navigation.navigateToEditContact
import dev.kyralabs.pingogram.feature.media.navigation.navigateToNewStory
import dev.kyralabs.pingogram.feature.media.navigation.navigateToStoryViewer
import dev.kyralabs.pingogram.ui.ChatsListDetail

/**
 * The Chats tab, [ChatsRoute]: the chat list beside the conversation and a page ([ChatsListDetail]); a chat opens with
 * [openChat], at a message or in a forum topic pushed as its chat would be, and media with [openMedia].
 */
internal fun NavGraphBuilder.chatsTab(
    navController: NavHostController,
    search: GlobalSearchState,
    headerActions: ChatListHeaderActions?,
    openChat: (chatId: Long) -> Unit,
    openMedia: (chatId: Long, messageId: Long) -> Unit,
) {
    val openMessage = { chatId: Long, messageId: Long ->
        navController.navigate(ChatRoute(chatId, messageId)) { launchSingleTop = true }
    }
    // The Chats tab lays out the chat list and the conversation side by side on expanded windows, and a profile or
    // an info page beside them on extra-large ones.
    composable<ChatsRoute> { entry ->
        val request by entry.savedStateHandle.chatToShow().collectAsStateWithLifecycle()
        // The conversation in the detail pane shares its media with the viewer, as the pushed one does.
        ProvideNavAnimatedVisibilityScope(this) {
            ChatsListDetail(
                search = search,
                headerActions = headerActions,
                onOpenArchive = { navController.navigate(ArchiveRoute) { launchSingleTop = true } },
                onOpenChat = { chatId, messageId ->
                    if (messageId == null) openChat(chatId) else openMessage(chatId, messageId)
                },
                onOpenMedia = openMedia,
                onOpenNewChat = { navController.navigate(NewChatRoute) { launchSingleTop = true } },
                onOpenProfile = navController::navigateToProfile,
                onOpenChatInfo = navController::navigateToChatInfo,
                onEditContact = navController::navigateToEditContact,
                onOpenStory = navController::navigateToStoryViewer,
                onNewStory = navController::navigateToNewStory,
                onEditFolder = navController::navigateToFolderEditor,
                chatToShow = request.toChatToShow(),
                onChatShown = { entry.savedStateHandle.chatShown() },
                onOpenScheduledMessages = navController::navigateToScheduledMessages,
                onOpenTopic = navController::navigateToTopic,
                onForward = { chatId, messageIds ->
                    navController.navigate(ForwardRoute(chatId, messageIds)) { launchSingleTop = true }
                },
            )
        }
    }
}
