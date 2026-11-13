package dev.kyralabs.pingogram.feature.chat.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.dialog
import dev.kyralabs.pingogram.core.navigation.TextStyleRoute
import dev.kyralabs.pingogram.core.ui.component.NoDialogWindowDim
import dev.kyralabs.pingogram.core.ui.component.SheetDialogProperties
import dev.kyralabs.pingogram.feature.chat.ui.ai.TextStyleSheetRoute

/**
 * Adds the sheet of a shared AI style, [TextStyleRoute], to the graph: a dialog destination like the forward sheet
 * ([forwardSheet]), so the screen below stays visible. [onClose] leaves it once the sheet closed.
 */
fun NavGraphBuilder.textStyleSheet(onClose: () -> Unit) {
    dialog<TextStyleRoute>(dialogProperties = SheetDialogProperties) {
        NoDialogWindowDim()
        TextStyleSheetRoute(onClose = onClose)
    }
}
