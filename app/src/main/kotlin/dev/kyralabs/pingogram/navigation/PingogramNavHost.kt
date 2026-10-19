package dev.kyralabs.pingogram.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
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
import dev.kyralabs.pingogram.core.ui.theme.SharedAxisDistance
import dev.kyralabs.pingogram.core.ui.theme.fadeThroughEnter
import dev.kyralabs.pingogram.core.ui.theme.fadeThroughExit
import dev.kyralabs.pingogram.core.ui.theme.predictiveBackPopEnter
import dev.kyralabs.pingogram.core.ui.theme.predictiveBackPopExit
import dev.kyralabs.pingogram.core.ui.theme.sharedAxisXEnter
import dev.kyralabs.pingogram.core.ui.theme.sharedAxisXExit
import dev.kyralabs.pingogram.core.ui.transition.LocalSharedTransitionScope
import dev.kyralabs.pingogram.core.ui.transition.ProvideNavAnimatedVisibilityScope
import dev.kyralabs.pingogram.feature.auth.navigation.authScreen
import dev.kyralabs.pingogram.feature.chat.navigation.chatScreen
import dev.kyralabs.pingogram.feature.chats.navigation.archiveScreen
import dev.kyralabs.pingogram.feature.chats.navigation.searchScreen
import dev.kyralabs.pingogram.feature.contacts.navigation.contactsScreen
import dev.kyralabs.pingogram.feature.media.navigation.mediaViewerScreen
import dev.kyralabs.pingogram.feature.media.navigation.navigateToMediaViewer
import dev.kyralabs.pingogram.feature.settings.navigation.popBackStackAfterProfileSaved
import dev.kyralabs.pingogram.feature.settings.navigation.settingsPages
import dev.kyralabs.pingogram.feature.settings.navigation.settingsScreen
import dev.kyralabs.pingogram.ui.ChatsListDetail

/**
 * The app's single `NavHost`: the login flow ([AuthRoute]) and the signed-in app ([MainGraph]). [uiState] (signed in
 * or out, never loading) picks the graph: the start destination when the `NavHost` first appears, and a reset of the
 * back stack whenever the two disagree (`REQUIREMENTS.md` FR-A8).
 *
 * Transitions follow `docs/DESIGN_SYSTEM.md` §5, built from the theme's expressive motion scheme:
 * - **Pushed screens** ([isPushed]: the conversation, the archive, search, settings pages) come in and go back with
 *   Material's shared axis on X ([sharedAxisXEnter], [sharedAxisXExit]), the screen below sliding the same way.
 * - **Tabs** switch with Material's fade-through, and so do the two graphs.
 * - **Full-screen destinations** ([isFullScreen], the media viewer) fade in and out on the default effects spring
 *   over the screen below, which stays as it is meanwhile, so the viewer's scrim darkens it while a photo grows out of
 *   its bubble.
 *
 * A predictive back gesture shrinks and dissolves any other screen it leaves over the one it goes back to
 * ([predictiveBackPopExit]); the viewer follows the gesture itself.
 *
 * The `NavHost` is a [SharedTransitionLayout], whose scope goes to the destinations through
 * [LocalSharedTransitionScope], and each destination that shares content provides its own animated visibility scope
 * ([ProvideNavAnimatedVisibilityScope]): the conversation and the media viewer share their photos, videos and GIFs
 * (the container transform of `sharedMediaBounds`).
 */
@Composable
internal fun PingogramNavHost(
    navController: NavHostController,
    uiState: MainActivityUiState,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val axisDistance = with(LocalDensity.current) { SharedAxisDistance.roundToPx() }
    // Saved with the back stack, so after rotation or process death the restored back stack finds the same graph.
    val startSignedIn = rememberSaveable { uiState == MainActivityUiState.SignedIn }
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = if (startSignedIn) MainGraph else AuthRoute,
                enterTransition = { enter(motionScheme, axisDistance) },
                exitTransition = { exit(motionScheme, axisDistance) },
                popEnterTransition = { popEnter(motionScheme, axisDistance) },
                popExitTransition = { popExit(motionScheme, axisDistance) },
                predictivePopEnterTransition = { predictiveBackPopEnter() },
                predictivePopExitTransition = {
                    if (initialState.destination.isFullScreen()) {
                        popExit(motionScheme, axisDistance)
                    } else {
                        predictiveBackPopExit(motionScheme)
                    }
                },
            ) {
                // MainActivityUiState turns SignedIn on the same step, and the effect below leaves the flow then.
                // Navigating here as well could act on a login that a logout has already replaced.
                authScreen(onAuthorized = {})
                mainGraph(navController)
            }
        }
    }
    SessionNavigationEffect(navController, uiState)
}

/**
 * A destination comes in: a full-screen one fades in over the screen below, a pushed one slides in on the shared axis
 * ([axisDistance] pixels), any other fades through.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(
    motionScheme: MotionScheme,
    axisDistance: Int,
): EnterTransition =
    when {
        targetState.destination.isFullScreen() -> fadeIn(motionScheme.defaultEffectsSpec())
        isPush() -> sharedAxisXEnter(motionScheme, axisDistance, forward = true)
        else -> fadeThroughEnter(motionScheme)
    }

/**
 * A destination is left for another: below a full-screen one it stays as it is meanwhile, until the `NavHost`'s
 * transition ends (the scope's `KeepUntilTransitionsFinished`); below a pushed one it slides away on the shared axis.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(
    motionScheme: MotionScheme,
    axisDistance: Int,
): ExitTransition =
    when {
        targetState.destination.isFullScreen() -> ExitTransition.KeepUntilTransitionsFinished
        isPush() -> sharedAxisXExit(motionScheme, axisDistance, forward = true)
        else -> fadeThroughExit(motionScheme)
    }

/**
 * Back to a destination: from a full-screen one it is there at once, below it; from a pushed one it slides back on the
 * shared axis.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(
    motionScheme: MotionScheme,
    axisDistance: Int,
): EnterTransition =
    when {
        initialState.destination.isFullScreen() -> EnterTransition.None
        isPop() -> sharedAxisXEnter(motionScheme, axisDistance, forward = false)
        else -> fadeThroughEnter(motionScheme)
    }

/** A destination is popped: a full-screen one fades out over the screen below, a pushed one slides back. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(
    motionScheme: MotionScheme,
    axisDistance: Int,
): ExitTransition =
    when {
        initialState.destination.isFullScreen() -> fadeOut(motionScheme.defaultEffectsSpec())
        isPop() -> sharedAxisXExit(motionScheme, axisDistance, forward = false)
        else -> fadeThroughExit(motionScheme)
    }

/** A screen is pushed above another of the signed-in app. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isPush(): Boolean =
    targetState.destination.isPushed() && initialState.destination.isInMainGraph()

/** A pushed screen goes back to another of the signed-in app. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isPop(): Boolean =
    initialState.destination.isPushed() && targetState.destination.isInMainGraph()

/**
 * A screen of the signed-in app pushed above a tab (the conversation, the archive, search, a settings page), as
 * opposed to a tab itself or a full-screen destination.
 */
private fun NavDestination.isPushed(): Boolean = isInMainGraph() && topLevelDestination() == null && !isFullScreen()

private fun NavGraphBuilder.mainGraph(navController: NavHostController) {
    navigation<MainGraph>(startDestination = TopLevelDestination.Start.route) {
        val openChat = { chatId: Long -> navController.navigate(ChatRoute(chatId)) { launchSingleTop = true } }
        // Full screen above everything, the list-detail layout too.
        val openMedia = { chatId: Long, messageId: Long -> navController.navigateToMediaViewer(chatId, messageId) }
        // The Chats tab lays out the chat list and the conversation side by side on expanded windows.
        composable<ChatsRoute> {
            // The conversation in the detail pane shares its media with the viewer, as the pushed one does.
            ProvideNavAnimatedVisibilityScope(this) {
                ChatsListDetail(
                    onOpenArchive = { navController.navigate(ArchiveRoute) { launchSingleTop = true } },
                    onOpenChat = openChat,
                    onOpenMedia = openMedia,
                )
            }
        }
        contactsScreen()
        // Settings pages are pushed above the tab on every window, as the archive is.
        settingsScreen(onOpen = { route -> navController.navigate(route) { launchSingleTop = true } })
        settingsPages(
            onOpen = { route -> navController.navigate(route) { launchSingleTop = true } },
            onBack = { navController.popBackStack() },
            onProfileSaved = { navController.popBackStackAfterProfileSaved() },
        )
        // Results push the conversation above search on every window, as the archive does: search is not a pane of
        // the Chats tab (it opens from every tab), and back returns to the results.
        searchScreen(
            onBack = { navController.popBackStack() },
            onOpenChat = openChat,
            onOpenMessage = { chatId, messageId ->
                navController.navigate(ChatRoute(chatId, messageId)) { launchSingleTop = true }
            },
        )
        archiveScreen(onBack = { navController.popBackStack() }, onOpenChat = openChat)
        chatScreen(onBack = { navController.popBackStack() }, onOpenMedia = openMedia)
        mediaViewerScreen(onBack = { navController.popBackStack() })
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
