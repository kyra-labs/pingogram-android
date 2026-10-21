package dev.kyralabs.pingogram.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.navigation.FloatingWindow
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navOptions
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.core.navigation.AuthRoute
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.core.navigation.MainGraph
import dev.kyralabs.pingogram.core.navigation.MediaViewerRoute
import dev.kyralabs.pingogram.feature.auth.navigation.navigateToAuth

/** Whether this destination belongs to the signed-in app, [MainGraph]. */
internal fun NavDestination?.isInMainGraph(): Boolean = this?.hierarchy?.any { it.hasRoute<MainGraph>() } == true

/**
 * Whether this destination of the signed-in app takes the whole window, without the navigation bar or rail: the media
 * viewer, [MediaViewerRoute].
 */
internal fun NavDestination?.isFullScreen(): Boolean = this?.hasRoute<MediaViewerRoute>() == true

/** Whether this destination is the login flow, [AuthRoute]. */
internal fun NavDestination?.isAuth(): Boolean = this?.hasRoute<AuthRoute>() == true

/** Replaces the whole back stack with the main graph, after logging in: Back leaves the app from there. */
internal fun NavController.navigateToMainGraph() {
    navigate(MainGraph) {
        popUpTo(graph.id) { inclusive = true }
    }
}

/**
 * Replaces the whole back stack with a new login flow, with a new `AuthViewModel` (`REQUIREMENTS.md` FR-A8).
 *
 * Tabs left with [navigateToTopLevelDestination] keep their saved back stacks, view models included, for
 * `restoreState`; they belong to the account that is leaving, so they go first. Clearing a saved stack restores it
 * and pops it again, so the start tab, whose saved stack sits on top of it, comes last.
 */
internal fun NavController.navigateToAuthGraph() {
    TopLevelDestination.entries.sortedBy { it == TopLevelDestination.Start }.forEach { clearBackStack(it.route) }
    navigateToAuth(navOptions { popUpTo(graph.id) { inclusive = true } })
}

/**
 * Opens chat [chatId] above the chat list, for a notification or a conversation shortcut: whatever was open above the
 * chat list is left, so back returns to the chat list, then leaves the app. Pushed on every window, as the archive
 * pushes chats.
 */
internal fun NavController.navigateToChatAboveList(chatId: Long) {
    navigate(ChatRoute(chatId)) {
        popUpTo(TopLevelDestination.Start.route)
        launchSingleTop = true
    }
}

/**
 * Leaves the media viewer for the conversation of chat [chatId] at message [messageId] ("Show in chat"): the viewer
 * goes, and the conversation opens at the message, pushed as from search, in place of the conversation of the same chat
 * the viewer was opened from, so back still leads where it did. On expanded windows, where the viewer opens above the
 * list-detail layout, the conversation is pushed above it, as search's results are.
 */
internal fun NavController.showMessageInChat(
    chatId: Long,
    messageId: Long,
) {
    if (currentDestination.isFullScreen()) popBackStack()
    val below = currentBackStackEntry
    val sameChat = below?.destination?.hasRoute<ChatRoute>() == true && below.toRoute<ChatRoute>().chatId == chatId
    navigate(ChatRoute(chatId, messageId)) {
        if (sameChat) popUpTo<ChatRoute> { inclusive = true }
    }
}

/**
 * The back stack entry whose screen the `NavHost` shows: the current one, or below dialog destinations (the forward
 * sheet, a [FloatingWindow]) the one under them, so that a dialog neither shows the navigation nor changes the tab.
 */
@Composable
internal fun NavController.screenEntryAsState(): State<NavBackStackEntry?> {
    val currentEntry = currentBackStackEntryAsState()
    val visible = visibleEntries.collectAsState()
    return remember(this, currentEntry, visible) {
        derivedStateOf {
            val entry = currentEntry.value
            if (entry?.destination is FloatingWindow) {
                visible.value.lastOrNull { it.destination !is FloatingWindow }
            } else {
                entry
            }
        }
    }
}
