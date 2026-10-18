package dev.kyralabs.pingogram.feature.chat.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.feature.chat.ui.ChatRoute
import dev.kyralabs.pingogram.core.navigation.ChatRoute as ChatDestination

/** Adds the conversation of a chat, [ChatDestination], to the graph; [onBack] leaves it. */
fun NavGraphBuilder.chatScreen(onBack: () -> Unit) {
    composable<ChatDestination> { ChatRoute(onBack = onBack) }
}
