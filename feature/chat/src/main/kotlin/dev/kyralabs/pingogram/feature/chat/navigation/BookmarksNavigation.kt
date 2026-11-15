package dev.kyralabs.pingogram.feature.chat.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.BookmarkTagsRoute
import dev.kyralabs.pingogram.core.navigation.BookmarksRoute
import dev.kyralabs.pingogram.feature.chat.ui.bookmarks.BookmarkTagsRoute as BookmarkTagsScreenRoute
import dev.kyralabs.pingogram.feature.chat.ui.bookmarks.BookmarksRoute as BookmarksScreenRoute

/**
 * Adds the Bookmarks page, [BookmarksRoute] (the owner's board 34), and its tag editor, [BookmarkTagsRoute] (34c),
 * to the graph: pushed above Settings › On this phone or Saved Messages, and above the page, with the shared axis on
 * X. [onBack] leaves either; a bookmark opens its message with [onOpenMessage]; the page's "Edit tags" calls
 * [onEditTags].
 */
fun NavGraphBuilder.bookmarksScreen(
    onBack: () -> Unit,
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
    onEditTags: () -> Unit,
) {
    composable<BookmarksRoute> {
        BookmarksScreenRoute(onBack = onBack, onOpenMessage = onOpenMessage, onEditTags = onEditTags)
    }
    composable<BookmarkTagsRoute> { BookmarkTagsScreenRoute(onBack = onBack) }
}

/** Pushes the bookmarks' tag editor above the Bookmarks page. */
fun NavController.navigateToBookmarkTags() {
    navigate(BookmarkTagsRoute) { launchSingleTop = true }
}

/** Pushes the Bookmarks page, unless it is shown already. */
fun NavController.navigateToBookmarks() {
    navigate(BookmarksRoute) { launchSingleTop = true }
}
