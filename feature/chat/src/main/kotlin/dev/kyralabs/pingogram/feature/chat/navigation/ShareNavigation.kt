package dev.kyralabs.pingogram.feature.chat.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.dialog
import dev.kyralabs.pingogram.core.navigation.ShareRoute
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareResultNotices
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareSheetRoute

/**
 * Adds the share sheet, [ShareRoute], to the graph (`REQUIREMENTS.md` FR-N2, direct share): a dialog destination like
 * the forward sheet ([forwardSheet]), above whatever screen shows, with only the sheet's own scrim. [onClose] leaves it
 * once the sheet closed; [onOpenChat] opens the one chat picked, whose composer then holds the share (`ShareInbox`).
 * Show the results of shares to several chats with [ShareNotices].
 */
fun NavGraphBuilder.shareSheet(
    onClose: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
) {
    dialog<ShareRoute>(
        dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        NoWindowDim()
        ShareSheetRoute(onClose = onClose, onOpenChat = onOpenChat)
    }
}

/**
 * The snackbars after sending a share to several chats, in [snackbarHostState]: to how many chats, or for how many it
 * failed and why. Compose it once, in the app shell, as the sheet's screen is gone by then.
 */
@Composable
fun ShareNotices(snackbarHostState: SnackbarHostState) {
    ShareResultNotices(snackbarHostState)
}
