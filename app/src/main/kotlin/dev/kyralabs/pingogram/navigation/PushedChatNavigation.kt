package dev.kyralabs.pingogram.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.feature.chat.navigation.chatScreen

/**
 * The conversation pushed above a tab. The chat messages were forwarded to goes above the conversation they came
 * from, so back returns there; so do a forum's topics and a message's thread of replies. The next unread channel takes
 * the read one's place ([navigateToChatInPlace]).
 */
internal fun NavGraphBuilder.pushedChat(
    navController: NavHostController,
    openMedia: (chatId: Long, messageId: Long) -> Unit,
) = chatScreen(
    onBack = { navController.popBackStack() },
    onOpenMedia = openMedia,
    onOpenChat = { chatId -> navController.navigate(ChatRoute(chatId)) },
    onOpenThread = { route -> navController.navigate(route) },
    onOpenProfile = navController::navigateToProfile,
    onOpenChatInfo = navController::navigateToChatInfo,
    onOpenNextChat = navController::navigateToChatInPlace,
)

/**
 * Opens chat [chatId] in place of the conversation on top, as the next unread channel replaces the read one: back
 * returns where it would have from that conversation.
 */
internal fun NavController.navigateToChatInPlace(chatId: Long) {
    navigate(ChatRoute(chatId)) { popUpTo<ChatRoute> { inclusive = true } }
}
