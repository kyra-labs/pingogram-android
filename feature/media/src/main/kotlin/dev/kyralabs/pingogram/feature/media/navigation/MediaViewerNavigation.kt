package dev.kyralabs.pingogram.feature.media.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import dagger.hilt.android.EntryPointAccessors
import dev.kyralabs.pingogram.core.navigation.MediaViewerRoute
import dev.kyralabs.pingogram.core.ui.navigation.overlay
import dev.kyralabs.pingogram.core.ui.transition.ProvideNavAnimatedVisibilityScope
import dev.kyralabs.pingogram.feature.media.pip.PipEntryPoint
import dev.kyralabs.pingogram.feature.media.ui.MediaViewerRoute as MediaViewer

/**
 * Adds the media viewer, [MediaViewerRoute], to the graph as an overlay destination (`core:ui`'s `OverlayNavigator`):
 * it fades in and out above the screen it opened from, which stays composed below it, so going back recomposes
 * nothing there and the media shrinks back into its bubble on frames that do nothing else. [onBack] leaves it, and
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
    overlay<MediaViewerRoute> {
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

/**
 * Opens the media viewer again at a video picture-in-picture gave back (its window expanded), which plays on there from
 * where the window left it. Called once by the app's navigation, which shows the viewer above whatever is open.
 */
@Composable
fun ReturnFromPictureInPictureEffect(navController: NavController) {
    if (LocalInspectionMode.current) return
    val context = LocalContext.current
    val videos =
        remember(context) {
            EntryPointAccessors.fromApplication(context.applicationContext, PipEntryPoint::class.java).videos()
        }
    val video by videos.returns.collectAsStateWithLifecycle()
    LaunchedEffect(video) {
        val given = video?.takeUnless { it.reopened } ?: return@LaunchedEffect
        videos.reopened(given)
        navController.navigateToMediaViewer(given.chatId, given.messageId)
    }
}
