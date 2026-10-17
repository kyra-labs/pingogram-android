package dev.kyralabs.pingogram.feature.chats.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.feature.chats.ui.ChatsScreen

/** Adds the Chats tab, [ChatsRoute], to the graph. */
fun NavGraphBuilder.chatsScreen() {
    composable<ChatsRoute> { ChatsScreen() }
}
