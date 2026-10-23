package dev.kyralabs.pingogram.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.core.navigation.ForwardRoute
import dev.kyralabs.pingogram.core.navigation.MediaViewerRoute
import dev.kyralabs.pingogram.feature.chat.navigation.ForwardNotices
import dev.kyralabs.pingogram.feature.chat.navigation.forwardSheet
import dev.kyralabs.pingogram.feature.media.navigation.mediaViewerScreen

/**
 * The media viewer, which closing removes from the back stack only while it is there, and the forward sheet its Forward
 * opens: a dialog above the viewer, which stays below it, and leaves once the sheet closed. After forwarding to one
 * chat, the viewer's snackbar opens that chat in the viewer's place, as the conversation's does: above the
 * conversation the viewer was opened from, so back returns there.
 */
internal fun NavGraphBuilder.mediaViewer(navController: NavHostController) {
    mediaViewerScreen(
        // The viewer and nothing below it: a second close, e.g. a swipe while its exit plays, does nothing.
        onBack = { navController.popBackStack<MediaViewerRoute>(inclusive = true) },
        onShowInChat = { chatId, messageId -> navController.showMessageInChat(chatId, messageId) },
        onForward = { chatId, messageIds ->
            navController.navigate(ForwardRoute(chatId, messageIds)) { launchSingleTop = true }
        },
        notices = { snackbarHostState ->
            ForwardNotices(snackbarHostState) { chatId ->
                navController.navigate(ChatRoute(chatId)) { popUpTo<MediaViewerRoute> { inclusive = true } }
            }
        },
    )
    forwardSheet(onClose = { navController.popBackStack<ForwardRoute>(inclusive = true) })
}
