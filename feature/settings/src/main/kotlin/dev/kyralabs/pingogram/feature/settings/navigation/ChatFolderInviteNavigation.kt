package dev.kyralabs.pingogram.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.dialog
import dev.kyralabs.pingogram.core.navigation.ChatFolderInviteRoute
import dev.kyralabs.pingogram.core.ui.component.NoDialogWindowDim
import dev.kyralabs.pingogram.core.ui.component.SheetDialogProperties
import dev.kyralabs.pingogram.feature.settings.ui.folders.invite.FolderInviteSheetRoute

/**
 * Adds the sheet of a shared folder's link, [ChatFolderInviteRoute] (`REQUIREMENTS.md` FR-L3), to the graph: a dialog
 * destination like the join sheet, so the screen the link was opened from stays visible below its `ModalBottomSheet`,
 * whose scrim is the only one ([NoDialogWindowDim]). [onClose] leaves the destination once the sheet closed.
 */
fun NavGraphBuilder.chatFolderInviteSheet(onClose: () -> Unit) {
    dialog<ChatFolderInviteRoute>(dialogProperties = SheetDialogProperties) {
        NoDialogWindowDim()
        FolderInviteSheetRoute(onClose = onClose)
    }
}
