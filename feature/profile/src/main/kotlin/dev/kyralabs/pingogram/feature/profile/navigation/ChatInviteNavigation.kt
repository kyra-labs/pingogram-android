package dev.kyralabs.pingogram.feature.profile.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.dialog
import dev.kyralabs.pingogram.core.navigation.ChatInviteRoute
import dev.kyralabs.pingogram.core.ui.component.NoDialogWindowDim
import dev.kyralabs.pingogram.core.ui.component.SheetDialogProperties
import dev.kyralabs.pingogram.feature.profile.invite.ChatInviteSheetRoute

/**
 * Adds the join sheet of an invite link, [ChatInviteRoute] (`REQUIREMENTS.md` FR-P5), to the graph: a dialog
 * destination like the forward sheet, so the screen the link was opened from stays visible below its
 * `ModalBottomSheet`, whose scrim is the only one ([NoDialogWindowDim]). Once the user is in the chat, [onOpenChat]
 * opens it in the sheet's place; [onClose] leaves the destination once the sheet closed.
 */
fun NavGraphBuilder.chatInviteSheet(
    onOpenChat: (chatId: Long) -> Unit,
    onClose: () -> Unit,
) {
    dialog<ChatInviteRoute>(dialogProperties = SheetDialogProperties) {
        NoDialogWindowDim()
        ChatInviteSheetRoute(onOpenChat = onOpenChat, onClose = onClose)
    }
}
