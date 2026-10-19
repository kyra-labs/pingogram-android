package dev.kyralabs.pingogram.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.kyralabs.pingogram.MainActivityUiState

/**
 * Opens [chatId], a chat a notification asked for, once the main graph shows, after `SessionNavigationEffect`
 * (`PingogramNavHost`) moved there, and reports it handled; drops it when signed out, as it belongs to an account that
 * is gone (or the login flow is in the way), and reports that too.
 */
@Composable
internal fun OpenChatEffect(
    navController: NavHostController,
    uiState: MainActivityUiState,
    chatId: Long?,
    onHandled: () -> Unit,
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val inMainGraph = currentEntry?.destination.isInMainGraph()
    val currentOnHandled by rememberUpdatedState(onHandled)
    LaunchedEffect(chatId, uiState, inMainGraph) {
        // Loading: wait until the login state is known.
        if (chatId == null || uiState == MainActivityUiState.Loading) return@LaunchedEffect
        if (uiState == MainActivityUiState.SignedIn) {
            if (!inMainGraph) return@LaunchedEffect
            navController.navigateToChatAboveList(chatId)
        }
        currentOnHandled()
    }
}
