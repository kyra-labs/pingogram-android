package dev.kyralabs.pingogram.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.feature.chat.navigation.bookmarksScreen
import dev.kyralabs.pingogram.feature.chat.navigation.chatExportScreen
import dev.kyralabs.pingogram.feature.chat.navigation.chatScreen
import dev.kyralabs.pingogram.feature.chat.navigation.navigateToScheduledMessages
import dev.kyralabs.pingogram.feature.chat.navigation.pinnedMessagesScreen
import dev.kyralabs.pingogram.feature.chat.navigation.scheduledMessagesScreen

/**
 * The pushed conversation and the pages of a chat pushed above what opens them, with the shared axis on X. The chat
 * messages were forwarded to goes above the conversation they came from, so back returns there; so do a forum's
 * topics, a message's thread of replies, the chat's scheduled messages (`REQUIREMENTS.md` FR-C5, from the
 * conversation's bar, also in the Chats tab's detail pane) and its pinned messages (from the pinned bar). The next
 * unread channel takes the read one's place
 * ([navigateToChatInPlace]). "Export chat" opens from a profile or an info page
 * (`docs/adr/0021-chat-export.md`). [openMedia] opens a photo, video or GIF of the conversation in the media viewer.
 */
internal fun NavGraphBuilder.chatScreens(
    navController: NavHostController,
    openMedia: (chatId: Long, messageId: Long) -> Unit,
) {
    chatScreen(
        onBack = { navController.popBackStack() },
        onOpenMedia = openMedia,
        onOpenChat = { chatId -> navController.navigate(ChatRoute(chatId)) },
        onOpenThread = { route -> navController.navigate(route) },
        onOpenProfile = navController::navigateToProfile,
        onOpenChatInfo = navController::navigateToChatInfo,
        onOpenScheduledMessages = navController::navigateToScheduledMessages,
        onOpenNextChat = navController::navigateToChatInPlace,
        onOpenMessage = { chatId, messageId -> navController.navigate(ChatRoute(chatId, messageId = messageId)) },
    )
    chatExportScreen(onBack = { navController.popBackStack() })
    scheduledMessagesScreen(onBack = { navController.popBackStack() })
    pinnedMessagesScreen(onBack = { navController.popBackStack() })
    // The bookmarks kept on this phone, from Settings › On this phone and Saved Messages (LocalBookmarksLauncher).
    bookmarksScreen(
        onBack = { navController.popBackStack() },
        onOpenMessage = { chatId, messageId -> navController.navigate(ChatRoute(chatId, messageId = messageId)) },
    )
}
