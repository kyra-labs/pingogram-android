package dev.kyralabs.pingogram.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.PageToOpen
import dev.kyralabs.pingogram.core.navigation.FocusRoute
import dev.kyralabs.pingogram.core.navigation.LockedChatsRoute
import dev.kyralabs.pingogram.core.navigation.WidgetChatPickerRoute
import dev.kyralabs.pingogram.core.navigation.WidgetReplyRoute

/**
 * Opens [chat], a chat a notification asked for, once the main graph shows, after `SessionNavigationEffect`
 * (`PingogramNavHost`) moved there, and reports it handled; drops it when signed out, as it belongs to an account that
 * is gone (or the login flow is in the way), and reports that too.
 */
@Composable
internal fun OpenChatEffect(
    navController: NavHostController,
    uiState: MainActivityUiState,
    chat: ChatToOpen?,
    onHandled: () -> Unit,
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val inMainGraph = currentEntry?.destination.isInMainGraph()
    val currentOnHandled by rememberUpdatedState(onHandled)
    LaunchedEffect(chat, uiState, inMainGraph) {
        // Loading: wait until the login state is known.
        if (chat == null || uiState == MainActivityUiState.Loading) return@LaunchedEffect
        if (uiState == MainActivityUiState.SignedIn) {
            if (!inMainGraph) return@LaunchedEffect
            navController.navigateToChatAboveList(chat.chatId, chat.messageId)
        }
        currentOnHandled()
    }
}

/**
 * Opens [page], which something outside the app asked for, once the main graph shows, and reports it handled; drops it
 * when signed out, as [OpenChatEffect]:
 * - Locked chats, which a locked chat's notification asks for (`docs/adr/0029-locked-chats.md`: it names no chat),
 *   above the chat list; the page asks for the passcode first.
 * - The Chats tab, whose Focus switcher the Focus tile's long press opened through `FocusSwitcherRequests` (board 31).
 * - Settings → Focus, which the Focus tile opens while there is no profile to turn on.
 * - A Conversation widget's chat picker, which its "Choose a chat" asks for (board 33), and its reply sheet, which its
 *   Reply asks for in the chat's account, above whatever shows.
 */
@Composable
internal fun OpenPageEffect(
    navController: NavHostController,
    uiState: MainActivityUiState,
    page: PageToOpen?,
    onHandled: () -> Unit,
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val inMainGraph = currentEntry?.destination.isInMainGraph()
    val currentOnHandled by rememberUpdatedState(onHandled)
    LaunchedEffect(page, uiState, inMainGraph) {
        if (page == null || uiState == MainActivityUiState.Loading) return@LaunchedEffect
        if (uiState == MainActivityUiState.SignedIn) {
            if (!inMainGraph) return@LaunchedEffect
            when (page) {
                PageToOpen.LockedChats -> navController.navigateAboveChats(LockedChatsRoute)
                PageToOpen.Focus -> navController.navigateAboveChats(FocusRoute)
                PageToOpen.FocusSwitcher -> navController.navigateToTopLevelDestination(TopLevelDestination.Chats)
                is PageToOpen.WidgetChat -> navController.navigate(WidgetChatPickerRoute(page.appWidgetId))
                is PageToOpen.WidgetReply -> navController.navigate(WidgetReplyRoute(page.chatId))
            }
        }
        currentOnHandled()
    }
}

private fun NavHostController.navigateAboveChats(route: Any) {
    navigate(route) {
        popUpTo(TopLevelDestination.Start.route)
        launchSingleTop = true
    }
}
