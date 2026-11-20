package dev.kyralabs.pingogram.navigation

import androidx.navigation.NavController
import dev.kyralabs.pingogram.core.navigation.ChatRoute

/**
 * Opens chat [chatId] in place of the conversation on top, as the next unread channel replaces the read one: back
 * returns where it would have from that conversation.
 */
internal fun NavController.navigateToChatInPlace(chatId: Long) = navigateToChatInPlace(ChatRoute(chatId))

/** Opens [destination] in place of the conversation on top, as the next unread forum topic replaces the read one. */
internal fun NavController.navigateToChatInPlace(destination: ChatRoute) {
    navigate(destination) { popUpTo<ChatRoute> { inclusive = true } }
}
