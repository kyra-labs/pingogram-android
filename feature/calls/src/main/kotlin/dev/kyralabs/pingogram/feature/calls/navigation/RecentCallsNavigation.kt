package dev.kyralabs.pingogram.feature.calls.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.RecentCallsRoute
import dev.kyralabs.pingogram.feature.calls.history.RecentCallsRoute as RecentCallsScreenRoute

/**
 * Adds Recent calls, [RecentCallsRoute], to the graph: pushed above the screen it is opened from (Settings home, the
 * Contacts tab). [onBack] closes it.
 */
fun NavGraphBuilder.recentCallsScreen(onBack: () -> Unit) {
    composable<RecentCallsRoute> { RecentCallsScreenRoute(onBack = onBack) }
}

/** Opens Recent calls, unless it shows already. */
fun NavController.navigateToRecentCalls() = navigate(RecentCallsRoute) { launchSingleTop = true }
