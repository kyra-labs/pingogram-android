package dev.kyralabs.pingogram.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dev.kyralabs.pingogram.core.domain.model.LinkTarget
import dev.kyralabs.pingogram.core.navigation.ChatFolderInviteRoute
import dev.kyralabs.pingogram.core.navigation.ChatInviteRoute
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.feature.profile.navigation.chatInviteSheet
import dev.kyralabs.pingogram.feature.scanner.navigation.qrScannerScreen
import dev.kyralabs.pingogram.feature.settings.navigation.chatFolderInviteSheet

/**
 * The QR code scanner (`REQUIREMENTS.md` FR-P5), full screen above the screen it was opened from. A Telegram link it
 * read closes it and goes to [onOpenLinkTarget] (`LinkDispatcher.show`), which opens the target's screen in its place:
 * a profile, a chat, the join sheet, the confirmation of a device's login.
 */
internal fun NavGraphBuilder.qrScanner(
    navController: NavHostController,
    onOpenLinkTarget: (LinkTarget) -> Unit,
) {
    qrScannerScreen(
        onBack = { navController.popBackStack() },
        onOpen = { target ->
            navController.popBackStack()
            onOpenLinkTarget(target)
        },
    )
}

/**
 * The join sheet of an invite link (`REQUIREMENTS.md` FR-P5), a dialog destination above the screen the link was
 * opened from. The chat the user joined (or was in already) opens in the sheet's place, so back returns to that screen.
 */
internal fun NavGraphBuilder.chatInvite(navController: NavHostController) {
    chatInviteSheet(
        onOpenChat = { chatId ->
            navController.popBackStack<ChatInviteRoute>(inclusive = true)
            navController.navigate(ChatRoute(chatId)) { launchSingleTop = true }
        },
        onClose = { navController.popBackStack<ChatInviteRoute>(inclusive = true) },
    )
}

/** The sheet of a shared folder's link (`t.me/addlist/…`), a dialog destination above the screen it was opened from. */
internal fun NavGraphBuilder.chatFolderInvite(navController: NavHostController) {
    chatFolderInviteSheet(onClose = { navController.popBackStack<ChatFolderInviteRoute>(inclusive = true) })
}
