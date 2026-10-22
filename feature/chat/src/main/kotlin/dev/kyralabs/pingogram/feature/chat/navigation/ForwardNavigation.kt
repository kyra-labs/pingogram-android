package dev.kyralabs.pingogram.feature.chat.navigation

import android.view.WindowManager
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.dialog
import dev.kyralabs.pingogram.core.navigation.ForwardRoute
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardResultNotices
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardSheetRoute

/**
 * Adds the forward sheet, [ForwardRoute], to the graph (`docs/ARCHITECTURE.md`, Navigation): a dialog destination, so
 * the screen it is opened from stays composed and visible below it, holding the conversation's `ModalBottomSheet`,
 * which draws its own scrim and follows the predictive back gesture. The dialog's own window is a transparent, edge to
 * edge one (`decorFitsSystemWindows = false`) that never dims what is below ([NoWindowDim]): only the sheet shows.
 * [onClose] leaves it once the sheet closed or could not open. Show its results with [ForwardNotices].
 */
fun NavGraphBuilder.forwardSheet(onClose: () -> Unit) {
    dialog<ForwardRoute>(
        dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        NoWindowDim()
        ForwardSheetRoute(onClose = onClose)
    }
}

/** The dialog window holding this composition dims nothing behind it: the sheet's scrim is the only one. */
@Composable
internal fun NoWindowDim() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window?.setDimAmount(0f)
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
