package dev.kyralabs.pingogram.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavController
import dev.kyralabs.pingogram.core.navigation.CatchUpRoute
import dev.kyralabs.pingogram.core.navigation.FeedRoute
import dev.kyralabs.pingogram.core.navigation.FocusRoute
import dev.kyralabs.pingogram.core.navigation.MentionsRoute
import dev.kyralabs.pingogram.feature.chats.navigation.ChatListHeaderActions
import dev.kyralabs.pingogram.feature.chats.navigation.GlobalSearchState
import dev.kyralabs.pingogram.navigation.TopLevelDestination
import dev.kyralabs.pingogram.navigation.navigateToTopLevelDestination

/**
 * What the Chats tab's header gets from the shell (`docs/DESIGN_SYSTEM.md` §6 "Chat list top bar"; owner decision
 * 2026-11-06): its search button opens global search ([openSearch]) and grows into it, away while [searchOpen]; the
 * signed-in user's avatar opens the Settings tab, and a long press on it the [accountMenu] where there is one, as on
 * the Settings tab ([AccountMenuAnchor]); the Catch-up chip pushes Catch-up. The Focus switcher's "Manage"
 * pushes Settings → Focus above the tab.
 */
@Composable
internal fun rememberChatListHeaderActions(
    navController: NavController,
    search: GlobalSearchState,
    searchOpen: State<Boolean>,
    openSearch: () -> Unit,
    accountMenu: AccountMenuActions?,
): ChatListHeaderActions {
    // Search docks or not by the window at the time of the tap.
    val currentOpenSearch by rememberUpdatedState(openSearch)
    return remember(navController, search, searchOpen, accountMenu) {
        ChatListHeaderActions(
            search = search,
            searchOpen = searchOpen,
            openSearch = { currentOpenSearch() },
            openAccount = { navController.navigateToTopLevelDestination(TopLevelDestination.Settings) },
            accountMenu = { content ->
                AccountMenuAnchor(
                    menu = accountMenu?.state,
                    onSwitch = { accountMenu?.onSwitch?.invoke(it) },
                    onAdd = { accountMenu?.onAdd?.invoke() },
                    content = content,
                )
            },
            openCatchUp = { navController.navigate(CatchUpRoute) { launchSingleTop = true } },
            openMentions = { navController.navigate(MentionsRoute) { launchSingleTop = true } },
            openFocus = { navController.navigate(FocusRoute) { launchSingleTop = true } },
            openFeed = { navController.navigate(FeedRoute) { launchSingleTop = true } },
        )
    }
}
