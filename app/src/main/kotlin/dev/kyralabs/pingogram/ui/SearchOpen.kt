package dev.kyralabs.pingogram.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.kyralabs.pingogram.core.navigation.SearchRoute
import dev.kyralabs.pingogram.feature.chats.navigation.GlobalSearchState

/**
 * Whether search shows: docked in the Chats tab's list pane ([GlobalSearchState.isDocked]), or anywhere on the back
 * stack, also below a conversation it opened. The search button is away meanwhile, so it never opens a second search
 * and only search's own coming and going moves it ([SearchButtonSlot]). Derived, so that only opening or leaving search
 * recomposes the shell.
 *
 * The back stack is asked for search's entry only while one may be on it ([LastSearchEntry]): the controller throws
 * where there is none, which, asked at every change of the destination, was most navigations.
 */
@Composable
internal fun rememberSearchOpen(
    navController: NavController,
    search: GlobalSearchState,
): State<Boolean> {
    val currentEntry = navController.currentBackStackEntryAsState()
    val lastSearch = remember(navController) { LastSearchEntry() }
    return remember(navController, currentEntry, search, lastSearch) {
        derivedStateOf {
            // Read for every change of the destination, which is when the back stack changes.
            lastSearch.saw(currentEntry.value)
            search.isDocked ||
                (lastSearch.mayBeOnStack() && runCatching { navController.getBackStackEntry<SearchRoute>() }.isSuccess)
        }
    }
}

/**
 * The last entry of search seen as the current destination: search can be on the back stack only until that entry is
 * destroyed, which Navigation does once it is popped and its way out has ended. Main thread only.
 */
internal class LastSearchEntry {
    private var entry: NavBackStackEntry? = null

    /** [current] is the destination now: kept if it is search. */
    fun saw(current: NavBackStackEntry?) {
        if (current?.destination?.hasRoute<SearchRoute>() == true) entry = current
    }

    /** False once no search entry is left that the back stack may still hold. */
    fun mayBeOnStack(): Boolean {
        val last = entry ?: return false
        if (last.lifecycle.currentState == Lifecycle.State.DESTROYED) entry = null
        return entry != null
    }
}
