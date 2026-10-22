package dev.kyralabs.pingogram.feature.chat.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.dialog
import dev.kyralabs.pingogram.core.navigation.ForwardRoute
import dev.kyralabs.pingogram.core.ui.component.NoDialogWindowDim
import dev.kyralabs.pingogram.core.ui.component.SheetDialogProperties
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardResultNotices
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardSheetRoute

/**
 * Adds the forward sheet, [ForwardRoute], to the graph (`docs/ARCHITECTURE.md`, Navigation): a dialog destination, so
 * the screen it is opened from stays composed and visible below it, holding the conversation's `ModalBottomSheet`,
 * which draws its own scrim and follows the predictive back gesture. The dialog's own window is a transparent, edge to
 * edge one (`decorFitsSystemWindows = false`) that never dims what is below ([NoDialogWindowDim]): only the sheet
 * shows. [onClose] leaves it once the sheet closed or could not open. Show its results with [ForwardNotices].
 */
fun NavGraphBuilder.forwardSheet(onClose: () -> Unit) {
    dialog<ForwardRoute>(dialogProperties = SheetDialogProperties) {
        NoDialogWindowDim()
        ForwardSheetRoute(onClose = onClose)
    }
}

/**
 * The snackbars after forwarding from a [ForwardRoute] opened above the screen of [snackbarHostState] (the media
 * viewer): to which chat or how many, with an action that opens the one other chat through [onOpenChat]; or for how
 * many it failed, and why. Compose it in that screen, so its snackbars show where the user is.
 */
@Composable
fun ForwardNotices(
    snackbarHostState: SnackbarHostState,
    onOpenChat: (chatId: Long) -> Unit,
) {
    ForwardResultNotices(snackbarHostState, onOpenChat)
}
