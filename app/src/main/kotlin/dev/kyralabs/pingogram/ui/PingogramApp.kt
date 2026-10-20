package dev.kyralabs.pingogram.ui

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.core.navigation.SearchRoute
import dev.kyralabs.pingogram.core.ui.component.HideNavigationBar
import dev.kyralabs.pingogram.core.ui.component.LocalNavigationBarInsets
import dev.kyralabs.pingogram.core.ui.component.LocalNavigationBarVisibility
import dev.kyralabs.pingogram.core.ui.component.NavigationBarVisibility
import dev.kyralabs.pingogram.core.ui.component.floatingToolbarInsets
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.transition.LocalSharedTransitionScope
import dev.kyralabs.pingogram.core.ui.transition.ProvideSharedTransitionScope
import dev.kyralabs.pingogram.feature.auth.ui.AuthProgressScreen
import dev.kyralabs.pingogram.feature.chats.navigation.GlobalSearchState
import dev.kyralabs.pingogram.feature.chats.navigation.rememberGlobalSearchState
import dev.kyralabs.pingogram.navigation.PingogramNavHost
import dev.kyralabs.pingogram.navigation.TopLevelDestination
import dev.kyralabs.pingogram.navigation.isFullScreen
import dev.kyralabs.pingogram.navigation.isInHierarchy
import dev.kyralabs.pingogram.navigation.isInMainGraph
import dev.kyralabs.pingogram.navigation.navigateToTopLevelDestination
import dev.kyralabs.pingogram.navigation.topLevelDestination

/**
 * The app shell: the `NavHost` and the app's navigation (`docs/DESIGN_SYSTEM.md` §6–7). [uiState] decides between the
 * login flow and the signed-in app; until it is known, after the splash screen gave up waiting, the shell shows
 * progress.
 *
 * Only the signed-in app has navigation; the login flow is full screen. The window size class picks it: in compact
 * windows the floating [PingogramNavigationBar] over the top-level screens, which hides on screens pushed on top of
 * them, keeping the tab below them selected ([FloatingNavigationBar]); in medium and wider windows and in windows of
 * compact height a `WideNavigationRail` with the search button at its top, from a `NavigationSuiteScaffold`, expanded
 * in large windows ([pingogramNavigationSuiteType]). The `NavHost` keeps its
 * place in the composition across both, so rotating or resizing the window never resets its screens. Full-screen
 * destinations (the media viewer) show neither.
 *
 * A top-level screen can hide the floating bar while it shows a floating toolbar of its own in the bar's place, e.g.
 * the chat list while chats are selected ([HideNavigationBar], through [navigationBarVisibility]). The bar's insets
 * stay meanwhile, so the screen's content keeps clearing the bottom.
 *
 * The bar's insets ([LocalNavigationBarInsets]) depend only on the window, not on the destination: the local is
 * static, so changing it recomposes everything below the `NavHost` without skipping, and pushing or popping a screen
 * (a conversation, the archive) would do that in the frames that start and end its transition. Screens pushed on top
 * of a tab do not read them.
 *
 * Global search (`docs/DESIGN_SYSTEM.md` §6 "Global search") opens from the search button: pushed full screen above
 * the tab in compact and medium windows, docked in the Chats tab's list pane in expanded and wider ones
 * ([GlobalSearchState]), which the Chats tab then shows. The button grows into search and back: the shell is a
 * [SharedTransitionLayout] around the navigation and the `NavHost`, whose scope goes down through
 * [LocalSharedTransitionScope] to both, and the button is away while search shows ([rememberSearchOpen]).
 *
 * [chatToOpen] (from a notification) is opened above the chat list once the account is logged in, then reported to
 * [onChatOpened]; nobody logged in drops it ([PingogramNavHost]).
 *
 * The selected tab comes from the back stack, so it survives rotation and process death along with the
 * `NavController` state, and stays right when back or a deep link changes the destination.
 */
@Composable
fun PingogramApp(
    uiState: MainActivityUiState,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    navigationBarVisibility: NavigationBarVisibility = remember { NavigationBarVisibility() },
    chatToOpen: Long? = null,
    onChatOpened: () -> Unit = {},
) {
    val currentEntry = navController.currentBackStackEntryAsState()
    // Derived, so that only entering or leaving the main graph or a tab recomposes the shell, not every destination.
    val inMainGraph by remember(currentEntry) { derivedStateOf { currentEntry.value?.destination.isInMainGraph() } }
    val showNavigation by remember(currentEntry) {
        derivedStateOf {
            val destination = currentEntry.value?.destination
            destination.isInMainGraph() && !destination.isFullScreen()
        }
    }
    val currentTab by remember(currentEntry) {
        derivedStateOf { currentEntry.value?.destination.topLevelDestination() }
    }
    // The tab the user is on: the current destination, or while screens pushed on top of a tab hide the floating bar,
    // that tab. A plain holder rather than state: the shell recomposes whenever the current tab changes.
    val lastTab = remember { LastTab() }
    val barTab = currentTab?.also { lastTab.tab = it } ?: lastTab.tab
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val adaptiveType = pingogramNavigationSuiteType(windowSizeClass)
    val floatingBar = adaptiveType == NavigationSuiteType.ShortNavigationBarCompact
    // The space the floating bar takes on the tabs; a screen may hide the bar itself there, never its space.
    val floatingBarSpace = inMainGraph && floatingBar
    val floatingBarSlot = showNavigation && floatingBar && currentTab != null
    val showRail = showNavigation && !floatingBar
    val search = rememberGlobalSearchState()
    val searchOpen by rememberSearchOpen(navController, search)
    val openSearch = { navController.openSearch(search, docked = windowSizeClass.docksSearch()) }
    SharedTransitionLayout(modifier) {
        ProvideSharedTransitionScope(this) {
            NavigationSuiteScaffold(
                navigationItems = { PingogramNavigationItems(navController, adaptiveType) },
                navigationSuiteType = if (showRail) adaptiveType else NavigationSuiteType.None,
                // The scaffold shows its primary action even without navigation.
                primaryActionContent = { if (showRail) RailSearchButton(adaptiveType, searchOpen, search, openSearch) },
            ) {
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(
                        LocalNavigationBarInsets provides
                            if (floatingBarSpace) floatingToolbarInsets() else WindowInsets(0, 0, 0, 0),
                        LocalNavigationBarVisibility provides navigationBarVisibility,
                    ) {
                        AppContent(uiState, navController, search, chatToOpen, onChatOpened)
                    }
                    // Compact windows only: composed there even while hidden (FloatingNavigationBar).
                    if (floatingBar) {
                        FloatingNavigationBar(
                            visible = floatingBarSlot && !navigationBarVisibility.isHidden,
                            selected = barTab,
                            onSelect = { navController.navigateToTopLevelDestination(it) },
                            onSearch = openSearch,
                            searchOpen = searchOpen,
                            search = search,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .padding(bottom = FloatingToolbarDefaults.ScreenOffset),
                        )
                    }
                }
            }
        }
    }
}

/** Progress while [uiState] is not known yet, the `NavHost` once it is; signed out, docked [search] closes. */
@Composable
private fun AppContent(
    uiState: MainActivityUiState,
    navController: NavHostController,
    search: GlobalSearchState,
    chatToOpen: Long?,
    onChatOpened: () -> Unit,
) {
    ResetSearchWhenSignedOut(uiState, search)
    if (uiState == MainActivityUiState.Loading) {
        // The same progress as the login flow's first step: TDLib is still starting.
        AuthProgressScreen(isLoggingOut = false)
    } else {
        PingogramNavHost(
            navController = navController,
            uiState = uiState,
            search = search,
            chatToOpen = chatToOpen,
            onChatOpened = onChatOpened,
        )
    }
}

/**
 * Whether search shows: docked in the Chats tab's list pane ([GlobalSearchState.isDocked]), or anywhere on the back
 * stack, also below a conversation it opened. The search button is away meanwhile, so it never opens a second search
 * and only search's own coming and going moves it ([SearchButtonSlot]). Derived, so that only opening or leaving search
 * recomposes the shell.
 */
@Composable
private fun rememberSearchOpen(
    navController: NavController,
    search: GlobalSearchState,
): State<Boolean> {
    val currentEntry = navController.currentBackStackEntryAsState()
    return remember(navController, currentEntry, search) {
        derivedStateOf {
            // Read for every change of the destination, which is when the back stack changes.
            currentEntry.value
            search.isDocked || runCatching { navController.getBackStackEntry<SearchRoute>() }.isSuccess
        }
    }
}

/**
 * Opens global search (`docs/DESIGN_SYSTEM.md` §6 "Global search"): [docked] in the Chats tab's list pane, which the
 * Chats tab shows first, or pushed full screen above the tab.
 */
private fun NavController.openSearch(
    search: GlobalSearchState,
    docked: Boolean,
) {
    if (docked) {
        search.isDocked = true
        if (currentDestination?.hasRoute<ChatsRoute>() != true) navigateToTopLevelDestination(TopLevelDestination.Chats)
    } else {
        navigate(SearchRoute) { launchSingleTop = true }
    }
}

/**
 * Where search docks (`docs/DESIGN_SYSTEM.md` §6, §7): in windows of expanded width and wider, where the Chats tab
 * shows its list beside the conversation. Compact and medium windows have one pane, and search is a destination.
 */
private fun WindowSizeClass.docksSearch(): Boolean =
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

/** Closes docked search when nobody is logged in, so the next login starts on the chat list. */
@Composable
private fun ResetSearchWhenSignedOut(
    uiState: MainActivityUiState,
    search: GlobalSearchState,
) {
    LaunchedEffect(uiState, search) { if (uiState == MainActivityUiState.SignedOut) search.isDocked = false }
}

/** The last tab the user was on, which the floating bar keeps showing while a pushed screen hides it. */
private class LastTab {
    var tab: TopLevelDestination? = null
}

/**
 * The navigation each window gets (`docs/DESIGN_SYSTEM.md` §6, §6.1): the floating bar in compact widths, a collapsed
 * rail in medium and expanded widths and in windows of compact height (phones in landscape, tabletop), where a bar
 * would take height that is scarce, and the rail expanded from large widths (1200 dp) on. Material's default
 * ([NavigationSuiteScaffoldDefaults.navigationSuiteType]) gives compact heights a short navigation bar and never
 * expands the rail.
 */
internal fun pingogramNavigationSuiteType(windowSizeClass: WindowSizeClass): NavigationSuiteType =
    when {
        !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) -> {
            NavigationSuiteType.WideNavigationRailCollapsed
        }

        !windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> {
            NavigationSuiteType.ShortNavigationBarCompact
        }

        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND) -> {
            NavigationSuiteType.WideNavigationRailExpanded
        }

        else -> {
            NavigationSuiteType.WideNavigationRailCollapsed
        }
    }

/**
 * One rail item per [TopLevelDestination]. It reads the back stack itself, so a destination change recomposes only
 * the items and never the `NavHost` content.
 */
@Composable
private fun PingogramNavigationItems(
    navController: NavController,
    navigationSuiteType: NavigationSuiteType,
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentEntry?.destination
    TopLevelDestination.entries.forEach { destination ->
        val selected = currentDestination.isInHierarchy(destination)
        NavigationSuiteItem(
            selected = selected,
            onClick = { navController.navigateToTopLevelDestination(destination) },
            icon = {
                Icon(
                    painter = painterResource(if (selected) destination.selectedIcon else destination.unselectedIcon),
                    // The label below names the item for accessibility services.
                    contentDescription = null,
                )
            },
            label = { Text(stringResource(destination.label)) },
            navigationSuiteType = navigationSuiteType,
        )
    }
}

// Signed in or out, the shell shows a destination with a Hilt view model (the chat list, the login flow), which
// previews cannot create: those screens have their own previews, and PingogramAppScreenshotTest captures the
// signed-in shell. PingogramNavigationBar has its own preview too.

@PingogramPreviews
@Composable
private fun PingogramAppLoadingPreview() {
    PingogramPreview { PingogramApp(uiState = MainActivityUiState.Loading) }
}
