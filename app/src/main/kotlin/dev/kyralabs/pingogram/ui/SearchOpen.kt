package dev.kyralabs.pingogram.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import dev.kyralabs.pingogram.core.navigation.SearchRoute
import dev.kyralabs.pingogram.feature.chats.navigation.GlobalSearchState

/**
 * Whether search shows: docked in the Chats tab's list pane ([GlobalSearchState.isDocked]), or anywhere on the back
 * stack, also below a conversation it opened. The search button is away meanwhile, so it never opens a second search
 * and only search's own coming and going moves it ([SearchButtonSlot]). Derived, so that only opening or leaving search
 * recomposes the shell.
 */
@Composable
internal fun rememberSearchOpen(
    navController: NavController,
    search: GlobalSearchState,
): State<Boolean> {
    val backStack = navController.currentBackStack.collectAsState()
    return remember(backStack, search) { derivedStateOf { search.isDocked || backStack.value.holdsSearch() } }
}

/**
 * Whether search is on this back stack, at the top or below a conversation it opened. Looked up in the list: asking
 * the controller for search's entry throws where there is none, which was most navigations.
 */
internal fun List<NavBackStackEntry>.holdsSearch(): Boolean = any { it.destination.hasRoute<SearchRoute>() }
