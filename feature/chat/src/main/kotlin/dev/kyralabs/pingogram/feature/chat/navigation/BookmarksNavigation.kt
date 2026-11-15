package dev.kyralabs.pingogram.feature.chat.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.BookmarksRoute
import dev.kyralabs.pingogram.feature.chat.ui.bookmarks.BookmarksRoute as BookmarksScreenRoute

/**
 * Adds the Bookmarks page, [BookmarksRoute] (the owner's board 34), to the graph: pushed above Settings › On this
 * phone or Saved Messages with the shared axis on X. [onBack] leaves it; a bookmark opens its message with
 * [onOpenMessage].
 */
fun NavGraphBuilder.bookmarksScreen(
    onBack: () -> Unit,
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
) {
    composable<BookmarksRoute> { BookmarksScreenRoute(onBack = onBack, onOpenMessage = onOpenMessage) }
}

/** Pushes the Bookmarks page, unless it is shown already. */
fun NavController.navigateToBookmarks() {
    navigate(BookmarksRoute) { launchSingleTop = true }
}
