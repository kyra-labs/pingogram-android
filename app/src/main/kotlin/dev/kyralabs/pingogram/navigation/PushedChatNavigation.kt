package dev.kyralabs.pingogram.navigation

import androidx.navigation.NavController
import dev.kyralabs.pingogram.core.navigation.ChatRoute

/**
 * Opens chat [chatId] in place of the conversation on top, as the next unread channel replaces the read one: back
 * returns where it would have from that conversation.
 */
internal fun NavController.navigateToChatInPlace(chatId: Long) {
    navigate(ChatRoute(chatId)) { popUpTo<ChatRoute> { inclusive = true } }
}
