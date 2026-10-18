package dev.kyralabs.pingogram.feature.chats.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.core.navigation.SearchRoute
import dev.kyralabs.pingogram.feature.chats.ui.ChatsScreen
import dev.kyralabs.pingogram.feature.chats.ui.SearchScreen

/** Adds the Chats tab, [ChatsRoute], to the graph. */
fun NavGraphBuilder.chatsScreen() {
    composable<ChatsRoute> { ChatsScreen() }
}

/** Adds global search, [SearchRoute], to the graph; [onBack] leaves it. */
fun NavGraphBuilder.searchScreen(onBack: () -> Unit) {
    composable<SearchRoute> { SearchScreen(onBack) }
}
