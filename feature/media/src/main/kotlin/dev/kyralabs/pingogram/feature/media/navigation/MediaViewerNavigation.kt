package dev.kyralabs.pingogram.feature.media.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.MediaViewerRoute
import dev.kyralabs.pingogram.core.ui.transition.ProvideNavAnimatedVisibilityScope
import dev.kyralabs.pingogram.feature.media.ui.MediaViewerRoute as MediaViewer

/**
 * Adds the media viewer, [MediaViewerRoute], to the graph, with the `NavHost`'s transitions; [onBack] leaves it, and
 * [onShowInChat] leaves it for the conversation of the chat at the shown message ("Show in chat"). [onForward] opens
 * the forward sheet for the shown message above it, and [notices] shows, in the viewer's snackbar host, what came of
 * forwarding (`feature:chat`, which this module does not depend on). The viewer handles
 * system back itself, following the predictive back gesture. The destination provides its animated visibility scope,
 * so the media grows out of its bubble in the conversation and shrinks back into it.
 */
fun NavGraphBuilder.mediaViewerScreen(
    onBack: () -> Unit,
    onShowInChat: (chatId: Long, messageId: Long) -> Unit,
    onForward: (chatId: Long, messageIds: List<Long>) -> Unit = { _, _ -> },
    notices: @Composable (SnackbarHostState) -> Unit = {},
) {
    composable<MediaViewerRoute> {
        ProvideNavAnimatedVisibilityScope(this) {
            MediaViewer(onBack = onBack, onShowInChat = onShowInChat, onForward = onForward, notices = notices)
        }
    }
}

/** Opens the media viewer on the photo, video or GIF [messageId] of chat [chatId]; once, however often it is tapped. */
fun NavController.navigateToMediaViewer(
    chatId: Long,
    messageId: Long,
) {
    navigate(MediaViewerRoute(chatId, messageId)) { launchSingleTop = true }
}
