package dev.kyralabs.pingogram.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.feature.auth.ui.AuthRoute
import dev.kyralabs.pingogram.core.navigation.AuthRoute as AuthDestination

/**
 * The login flow as one destination ([AuthDestination] from `core:navigation`). [onAuthorized] runs once the account
 * is logged in; the app then replaces the flow with the main graph.
 */
fun NavGraphBuilder.authScreen(onAuthorized: () -> Unit) {
    composable<AuthDestination> {
        AuthRoute(onAuthorized = onAuthorized)
    }
}

/** Opens the login flow, e.g. after logging out (FR-A8). */
fun NavController.navigateToAuth(navOptions: NavOptions? = null) {
    navigate(AuthDestination, navOptions)
}
