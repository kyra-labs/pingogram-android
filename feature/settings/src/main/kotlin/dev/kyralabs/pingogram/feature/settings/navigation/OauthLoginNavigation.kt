package dev.kyralabs.pingogram.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.dialog
import dev.kyralabs.pingogram.core.navigation.OauthLoginRoute
import dev.kyralabs.pingogram.core.ui.component.NoDialogWindowDim
import dev.kyralabs.pingogram.core.ui.component.SheetDialogProperties
import dev.kyralabs.pingogram.feature.settings.ui.oauth.OauthLoginRoute as OauthLoginSheetRoute

/**
 * Adds the sheet of a "Log in with Telegram" request, [OauthLoginRoute], to the graph: a dialog destination like the
 * join sheet, so the screen it was opened from stays visible below its `ModalBottomSheet`, whose scrim is the only one
 * ([NoDialogWindowDim]). [onClose] leaves the destination once the sheet closed.
 */
fun NavGraphBuilder.oauthLoginSheet(onClose: () -> Unit) {
    dialog<OauthLoginRoute>(dialogProperties = SheetDialogProperties) {
        NoDialogWindowDim()
        OauthLoginSheetRoute(onClose = onClose)
    }
}
