package dev.kyralabs.pingogram.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.navOptions
import dev.kyralabs.pingogram.core.navigation.AuthRoute
import dev.kyralabs.pingogram.core.navigation.MainGraph
import dev.kyralabs.pingogram.feature.auth.navigation.navigateToAuth

/** Whether this destination belongs to the signed-in app, [MainGraph]. */
internal fun NavDestination?.isInMainGraph(): Boolean = this?.hierarchy?.any { it.hasRoute<MainGraph>() } == true

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
