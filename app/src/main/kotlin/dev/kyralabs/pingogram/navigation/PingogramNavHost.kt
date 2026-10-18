package dev.kyralabs.pingogram.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.core.navigation.ArchiveRoute
import dev.kyralabs.pingogram.core.navigation.AuthRoute
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.core.navigation.MainGraph
import dev.kyralabs.pingogram.core.ui.theme.fadeThroughEnter
import dev.kyralabs.pingogram.core.ui.theme.fadeThroughExit
import dev.kyralabs.pingogram.feature.auth.navigation.authScreen
import dev.kyralabs.pingogram.feature.chat.navigation.chatScreen
import dev.kyralabs.pingogram.feature.chats.navigation.archiveScreen
import dev.kyralabs.pingogram.feature.chats.navigation.searchScreen
import dev.kyralabs.pingogram.feature.contacts.navigation.contactsScreen
import dev.kyralabs.pingogram.feature.settings.navigation.settingsScreen
import dev.kyralabs.pingogram.ui.ChatsListDetail

/**
 * The app's single `NavHost`: the login flow ([AuthRoute]) and the signed-in app ([MainGraph]). [uiState] (signed in
 * or out, never loading) picks the graph: the start destination when the `NavHost` first appears, and a reset of the
 * back stack whenever the two disagree (`REQUIREMENTS.md` FR-A8).
 *
 * Destinations switch with Material's fade-through (`docs/DESIGN_SYSTEM.md` §5), built from the theme's
 * expressive motion scheme; so does the switch between the two graphs. Destinations pushed on top of a tab later bring
 * their own shared-axis transitions.
 */
@Composable
internal fun PingogramNavHost(
    navController: NavHostController,
    uiState: MainActivityUiState,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    // Saved with the back stack, so after rotation or process death the restored back stack finds the same graph.
    val startSignedIn = rememberSaveable { uiState == MainActivityUiState.SignedIn }
    NavHost(
        navController = navController,
        startDestination = if (startSignedIn) MainGraph else AuthRoute,
        modifier = modifier,
        enterTransition = { fadeThroughEnter(motionScheme) },
        exitTransition = { fadeThroughExit(motionScheme) },
    ) {
        // MainActivityUiState turns SignedIn on the same step, and the effect below leaves the flow then. Navigating
        // here as well could act on a login that a logout has already replaced.
        authScreen(onAuthorized = {})
        mainGraph(navController)
    }
    SessionNavigationEffect(navController, uiState)
}

private fun NavGraphBuilder.mainGraph(navController: NavHostController) {
    navigation<MainGraph>(startDestination = TopLevelDestination.Start.route) {
        val openChat = { chatId: Long -> navController.navigate(ChatRoute(chatId)) { launchSingleTop = true } }
        // The Chats tab lays out the chat list and the conversation side by side on expanded windows.
        composable<ChatsRoute> {
            ChatsListDetail(
                onOpenArchive = { navController.navigate(ArchiveRoute) { launchSingleTop = true } },
                onOpenChat = openChat,
            )
        }
        contactsScreen()
        settingsScreen()
        searchScreen(onBack = { navController.popBackStack() })
        archiveScreen(onBack = { navController.popBackStack() }, onOpenChat = openChat)
        chatScreen(onBack = { navController.popBackStack() })
    }
}

/**
 * Moves the back stack into the graph [uiState] calls for whenever the state or the destination changes: the main
 * graph once logged in; a new login flow for any other step, whether the user logged out here or on another device,
 * the session was revoked, or TDLib started over after `Closed`. Both moves replace the whole back stack, so Back
 * never leads into the other graph.
 */
@Composable
private fun SessionNavigationEffect(
    navController: NavHostController,
    uiState: MainActivityUiState,
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val destination = currentEntry?.destination
    LaunchedEffect(uiState, destination) {
        // No destination yet: the NavHost has not set its graph.
        if (destination == null) return@LaunchedEffect
        when (uiState) {
            MainActivityUiState.SignedIn -> if (!destination.isInMainGraph()) navController.navigateToMainGraph()
            MainActivityUiState.SignedOut -> if (!destination.isAuth()) navController.navigateToAuthGraph()
            MainActivityUiState.Loading -> Unit
        }
    }
}
