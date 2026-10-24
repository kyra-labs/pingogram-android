package dev.kyralabs.pingogram.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.core.domain.model.LinkTarget
import dev.kyralabs.pingogram.core.navigation.ArchiveRoute
import dev.kyralabs.pingogram.core.navigation.AuthRoute
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.core.navigation.MainGraph
import dev.kyralabs.pingogram.core.navigation.NewChatRoute
import dev.kyralabs.pingogram.core.navigation.StoryViewerRoute
import dev.kyralabs.pingogram.core.ui.theme.SharedAxisDistance
import dev.kyralabs.pingogram.core.ui.theme.fadeThroughEnter
import dev.kyralabs.pingogram.core.ui.theme.fadeThroughExit
import dev.kyralabs.pingogram.core.ui.theme.predictiveBackPopEnter
import dev.kyralabs.pingogram.core.ui.theme.predictiveBackPopExit
import dev.kyralabs.pingogram.core.ui.theme.sharedAxisXEnter
import dev.kyralabs.pingogram.core.ui.theme.sharedAxisXExit
import dev.kyralabs.pingogram.core.ui.transition.ProvideNavAnimatedVisibilityScope
import dev.kyralabs.pingogram.core.ui.transition.ProvideSharedTransitionScope
import dev.kyralabs.pingogram.feature.auth.navigation.authScreen
import dev.kyralabs.pingogram.feature.chat.navigation.chatExportScreen
import dev.kyralabs.pingogram.feature.chat.navigation.chatScreen
import dev.kyralabs.pingogram.feature.chats.navigation.GlobalSearchState
import dev.kyralabs.pingogram.feature.chats.navigation.archiveScreen
import dev.kyralabs.pingogram.feature.chats.navigation.searchScreen
import dev.kyralabs.pingogram.feature.contacts.navigation.navigateToEditContact
import dev.kyralabs.pingogram.feature.contacts.navigation.navigateToNewContact
import dev.kyralabs.pingogram.feature.feedback.navigation.feedbackScreen
import dev.kyralabs.pingogram.feature.media.navigation.ReturnFromPictureInPictureEffect
import dev.kyralabs.pingogram.feature.media.navigation.navigateToMediaViewer
import dev.kyralabs.pingogram.feature.media.navigation.navigateToStoryViewer
import dev.kyralabs.pingogram.feature.media.navigation.storyViewerScreen
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
 * The `NavHost` sits in the app shell's `SharedTransitionLayout`, whose scope comes through
 * `LocalSharedTransitionScope` (`PingogramApp`, [ProvideSharedTransitionScope]), and each destination that shares
 * content provides its own animated visibility scope ([ProvideNavAnimatedVisibilityScope]): the conversation and the
 * media viewer share their photos, videos and GIFs (the container transform of `sharedMediaBounds`, morphing between
 * the media's corners in its bubble and the viewer's), the Chats tab's "New chat" button grows into the New chat
 * screen (`containerTransform`), and the shell's search button into search ([search], full screen or docked in the
 * Chats tab's list pane).
 *
 * The media viewer's Forward opens the forward sheet, a dialog destination of `feature:chat`, above it, and the viewer
 * shows what came of it ([mediaViewer]). What another app shares opens the share sheet, another, above any screen
 * ([share]). A user's profile ([profile]) is pushed above the conversation whose title, a sender's avatar or name, or a
 * mention opened it, growing out of the title's avatar (`avatarTransform`), and so is a group's or channel's info page
 * ([chatInfo]); on extra-large windows the Chats tab opens both beside the conversation instead. On expanded and wider
 * windows, a chat that search, a deep link or a profile opens shows in the Chats tab's detail pane ([navigateToChat]).
 *
 * [chatToOpen], a chat a notification asked for, opens above the chat list ([navigateToChatAboveList]) once the main
 * graph shows, and is reported to [onChatOpened]; signed out, it is dropped and reported too.
 *
 * The QR code scanner (from the Contacts tab, the user's QR code, or Active sessions' "Link desktop device") is full
 * screen; a Telegram link it read closes it and opens through [onOpenLinkTarget], the app's link dispatcher, as a
 * tapped link would. An invitation's join sheet is a dialog destination like the forward sheet ([chatInvite]).
 */
@Composable
internal fun PingogramNavHost(
    navController: NavHostController,
    uiState: MainActivityUiState,
    search: GlobalSearchState,
    modifier: Modifier = Modifier,
    chatToOpen: Long? = null,
    onChatOpened: () -> Unit = {},
    onOpenLinkTarget: (LinkTarget) -> Unit = {},
) {
    val motionScheme = MaterialTheme.motionScheme
    val axisDistance = with(LocalDensity.current) { SharedAxisDistance.roundToPx() }
    // Saved with the back stack, so after rotation or process death the restored back stack finds the same graph.
    val startSignedIn = rememberSaveable { uiState == MainActivityUiState.SignedIn }
    // Read by the callbacks when they run, so the graph is not built again as the window changes.
    val besideList = rememberChatBesideList()
    NavHost(
        navController = navController,
        startDestination = if (startSignedIn) MainGraph else AuthRoute,
        modifier = modifier,
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
        mainGraph(navController, search, besideList, onOpenLinkTarget)
    }
    SessionNavigationEffect(navController, uiState)
    OpenChatEffect(navController, uiState, chatToOpen, onChatOpened)
    ReturnFromPictureInPictureEffect(navController)
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

private fun NavGraphBuilder.mainGraph(
    navController: NavHostController,
    search: GlobalSearchState,
    besideList: State<Boolean>,
    onOpenLinkTarget: (LinkTarget) -> Unit,
) {
    navigation<MainGraph>(startDestination = TopLevelDestination.Start.route) {
        val openChat = { chatId: Long -> navController.navigate(ChatRoute(chatId)) { launchSingleTop = true } }
        val openMessage = { chatId: Long, messageId: Long ->
            navController.navigate(ChatRoute(chatId, messageId)) { launchSingleTop = true }
        }
        // Full screen above everything, the list-detail layout too.
        val openMedia = { chatId: Long, messageId: Long -> navController.navigateToMediaViewer(chatId, messageId) }
        // The Chats tab lays out the chat list and the conversation side by side on expanded windows, and a profile or
        // an info page beside them on extra-large ones.
        composable<ChatsRoute> { entry ->
            val request by entry.savedStateHandle.chatToShow().collectAsStateWithLifecycle()
            // The conversation in the detail pane shares its media with the viewer, as the pushed one does.
            ProvideNavAnimatedVisibilityScope(this) {
                ChatsListDetail(
                    search = search,
                    onOpenArchive = { navController.navigate(ArchiveRoute) { launchSingleTop = true } },
                    onOpenChat = { chatId, messageId ->
                        if (messageId == null) openChat(chatId) else openMessage(chatId, messageId)
                    },
                    onOpenMedia = openMedia,
                    onOpenNewChat = { navController.navigate(NewChatRoute) { launchSingleTop = true } },
                    onOpenProfile = navController::navigateToProfile,
                    onOpenChatInfo = navController::navigateToChatInfo,
                    onEditContact = navController::navigateToEditContact,
                    onOpenStory = navController::navigateToStoryViewer,
                    chatToShow = request.toChatToShow(),
                    onChatShown = { entry.savedStateHandle.chatShown() },
                )
            }
        }
        contacts(navController, openChat)
        settings(navController)
        qrScanner(navController, onOpenLinkTarget)
        // "Send feedback", from Settings → About (LocalFeedbackLauncher, provided by PingogramApp).
        feedbackScreen(onBack = { navController.popBackStack() })
        // Full-screen search, on compact and medium windows (expanded ones dock it in the Chats tab's list pane):
        // results push the conversation above it, as the archive does, and back returns to the results. In a window
        // that grew while it showed, they open beside the chat list.
        searchScreen(
            state = search,
            onBack = { navController.popBackStack() },
            onOpenChat = { chatId -> navController.navigateToChat(chatId, besideList = besideList.value) },
            onOpenMessage = { chatId, messageId ->
                navController.navigateToChat(chatId, messageId, besideList = besideList.value)
            },
        )
        archiveScreen(onBack = { navController.popBackStack() }, onOpenChat = openChat)
        newChatScreens(navController)
        // The chat messages were forwarded to goes above the conversation they came from, so back returns there; so do
        // a forum's topics and a message's thread of replies.
        chatScreen(
            onBack = { navController.popBackStack() },
            onOpenMedia = openMedia,
            onOpenChat = { chatId -> navController.navigate(ChatRoute(chatId)) },
            onOpenThread = { route -> navController.navigate(route) },
            onOpenProfile = navController::navigateToProfile,
            onOpenChatInfo = navController::navigateToChatInfo,
        )
        profile(navController, besideList)
        chatInfo(navController, besideList)
        // "Export chat", from a profile or an info page (docs/adr/0021-chat-export.md).
        chatExportScreen(onBack = { navController.popBackStack() })
        chatInvite(navController)
        chatFolderInvite(navController)
        mediaViewer(navController)
        // The viewer and nothing below it: a second close, e.g. a swipe while its exit plays, does nothing.
        storyViewerScreen(onClose = { navController.popBackStack<StoryViewerRoute>(inclusive = true) })
        share(navController)
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
