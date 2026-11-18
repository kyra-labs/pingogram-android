package dev.kyralabs.pingogram.feature.chats.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.feature.chats.ui.feed.FeedRoute
import dev.kyralabs.pingogram.core.navigation.FeedRoute as FeedDestination

/**
 * Adds the Channel feed (`REQUIREMENTS.md` FR-X5), [FeedDestination], to the graph; [onBack] returns to the chats, and
 * a card opens its channel at the post through [onOpenPost].
 */
fun NavGraphBuilder.feedScreen(
    onBack: () -> Unit,
    onOpenPost: (chatId: Long, messageId: Long) -> Unit,
) {
    composable<FeedDestination> { FeedRoute(onBack = onBack, onOpenPost = onOpenPost) }
}
