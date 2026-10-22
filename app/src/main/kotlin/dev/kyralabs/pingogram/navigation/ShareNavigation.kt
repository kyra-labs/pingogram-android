package dev.kyralabs.pingogram.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.core.domain.model.SharedContent
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.core.navigation.ShareRoute
import dev.kyralabs.pingogram.feature.chat.navigation.shareSheet
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareInbox
import dev.kyralabs.pingogram.share.IncomingShare

/**
 * The share sheet (`REQUIREMENTS.md` FR-N2, direct share), a dialog destination of `feature:chat` above whatever
 * screen shows: it leaves once closed, and the one chat picked in it opens above that screen, the share waiting in its
 * composer.
 */
internal fun NavGraphBuilder.share(navController: NavHostController) {
    shareSheet(
        onClose = { navController.popBackStack<ShareRoute>(inclusive = true) },
        onOpenChat = { chatId ->
            navController.popBackStack<ShareRoute>(inclusive = true)
            navController.openSharedChat(chatId, fromOutside = false)
        },
    )
}

/**
 * Hands [share], what another app shared with Pingogram, to [onOpen] once the main graph shows, and reports it handled
 * ([onHandled]): like a Telegram link (`OpenLinkEffect`), signed out it waits for the login, and the passcode lock
 * holds it, as this effect is not composed while locked.
 */
@Composable
internal fun OpenShareEffect(
    navController: NavHostController,
    uiState: MainActivityUiState,
    share: IncomingShare?,
    onOpen: (IncomingShare) -> Unit,
    onHandled: () -> Unit,
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val inMainGraph = currentEntry?.destination.isInMainGraph()
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnHandled by rememberUpdatedState(onHandled)
    LaunchedEffect(share, uiState, inMainGraph) {
        if (share == null || uiState != MainActivityUiState.SignedIn || !inMainGraph) return@LaunchedEffect
        currentOnOpen(share)
        currentOnHandled()
    }
}

/**
 * Opens what was shared ([content]) through [inbox]: for chat [chatId] (a Direct Share target), that chat with the
 * share in its composer, above the chat list when it came from another app ([fromOutside]), as from a notification;
 * otherwise the share sheet, in place of one already open, to pick the chats it goes to.
 */
internal fun NavHostController.openShare(
    content: SharedContent,
    chatId: Long?,
    inbox: ShareInbox,
    fromOutside: Boolean,
) {
    if (chatId != null) {
        inbox.deliver(chatId, content)
        openSharedChat(chatId, fromOutside)
    } else {
        navigate(ShareRoute(inbox.offer(content))) { popUpTo<ShareRoute> { inclusive = true } }
    }
}

/**
 * Shows the conversation of chat [chatId], which takes the share delivered to it: nothing new when it shows already,
 * else pushed, [fromOutside] above the chat list.
 */
private fun NavHostController.openSharedChat(
    chatId: Long,
    fromOutside: Boolean,
) {
    val current = currentBackStackEntry
    val openChat = current?.takeIf { it.destination.hasRoute<ChatRoute>() }?.toRoute<ChatRoute>()
    when {
        openChat?.chatId == chatId -> Unit
        fromOutside -> navigateToChatAboveList(chatId)
        else -> navigate(ChatRoute(chatId)) { launchSingleTop = true }
    }
}
