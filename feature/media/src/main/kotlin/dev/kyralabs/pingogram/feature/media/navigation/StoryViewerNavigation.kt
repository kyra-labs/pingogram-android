package dev.kyralabs.pingogram.feature.media.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.StoryViewerRoute
import dev.kyralabs.pingogram.feature.media.story.StoryViewerRoute as StoryViewer

/**
 * Adds the stories viewer, [StoryViewerRoute], to the graph, with the `NavHost`'s transitions for full-screen
 * destinations; [onClose] leaves it, after the last story or by a swipe, back or the close button; [onShare] opens the
 * share sheet for a story, above the viewer, which pauses meanwhile. The viewer handles system back itself, following
 * the predictive back gesture.
 */
fun NavGraphBuilder.storyViewerScreen(
    onClose: () -> Unit,
    onShare: (posterChatId: Long, storyId: Int) -> Unit = { _, _ -> },
) {
    composable<StoryViewerRoute> { StoryViewer(onClose = onClose, onShare = onShare) }
}

/** Opens the stories viewer on chat [chatId]'s stories; once, however often its ring is tapped. */
fun NavController.navigateToStoryViewer(chatId: Long) {
    navigate(StoryViewerRoute(chatId)) { launchSingleTop = true }
}
