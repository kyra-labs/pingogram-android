package dev.kyralabs.pingogram.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.navigation.SearchRoute
import dev.kyralabs.pingogram.core.ui.component.HideNavigationBar
import dev.kyralabs.pingogram.core.ui.component.LocalNavigationBarInsets
import dev.kyralabs.pingogram.core.ui.component.LocalNavigationBarVisibility
import dev.kyralabs.pingogram.core.ui.component.NavigationBarVisibility
import dev.kyralabs.pingogram.core.ui.component.floatingToolbarInsets
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.auth.ui.AuthProgressScreen
import dev.kyralabs.pingogram.navigation.PingogramNavHost
import dev.kyralabs.pingogram.navigation.TopLevelDestination
import dev.kyralabs.pingogram.navigation.isFullScreen
import dev.kyralabs.pingogram.navigation.isInHierarchy
import dev.kyralabs.pingogram.navigation.isInMainGraph
import dev.kyralabs.pingogram.navigation.navigateToTopLevelDestination
import dev.kyralabs.pingogram.navigation.topLevelDestination
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The app shell: the `NavHost` and the app's navigation (`docs/DESIGN_SYSTEM.md` §6–7). [uiState] decides between the
 * login flow and the signed-in app; until it is known, after the splash screen gave up waiting, the shell shows
 * progress.
 *
 * Only the signed-in app has navigation; the login flow is full screen. The window size class picks it: in compact
 * windows the floating [PingogramNavigationBar] over the top-level screens, which hides on screens pushed on top of
 * them, keeping the tab below them selected ([FloatingNavigationBar]); in medium and wider windows a collapsed
 * `WideNavigationRail` with the search button at its top, from a `NavigationSuiteScaffold`. The `NavHost` keeps its
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
 * The selected tab comes from the back stack, so it survives rotation and process death along with the
 * `NavController` state, and stays right when back or a deep link changes the destination.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PingogramApp(
    uiState: MainActivityUiState,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    navigationBarVisibility: NavigationBarVisibility = remember { NavigationBarVisibility() },
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
    val adaptiveType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    val floatingBar = adaptiveType.isNavigationBar()
    // The space the floating bar takes on the tabs; a screen may hide the bar itself there, never its space.
    val floatingBarSpace = inMainGraph && floatingBar
    val floatingBarSlot = showNavigation && floatingBar && currentTab != null
    val showRail = showNavigation && !floatingBar
    val openSearch = { navController.navigate(SearchRoute) { launchSingleTop = true } }
    NavigationSuiteScaffold(
        navigationItems = { PingogramNavigationItems(navController, adaptiveType) },
        modifier = modifier,
        navigationSuiteType = if (showRail) adaptiveType else NavigationSuiteType.None,
        // The scaffold shows its primary action even without navigation.
        primaryActionContent = { if (showRail) SearchButton(onClick = openSearch) },
    ) {
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalNavigationBarInsets provides
                    if (floatingBarSpace) floatingToolbarInsets() else WindowInsets(0, 0, 0, 0),
                LocalNavigationBarVisibility provides navigationBarVisibility,
            ) {
                if (uiState == MainActivityUiState.Loading) {
                    // The same progress as the login flow's first step: TDLib is still starting.
                    AuthProgressScreen(isLoggingOut = false)
                } else {
                    PingogramNavHost(navController, uiState)
                }
            }
            // Compact windows only: composed there even while hidden (FloatingNavigationBar).
            if (floatingBar) {
                FloatingNavigationBar(
                    visible = floatingBarSlot && !navigationBarVisibility.isHidden,
                    selected = barTab,
                    onSelect = { navController.navigateToTopLevelDestination(it) },
                    onSearch = openSearch,
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

/** The last tab the user was on, which the floating bar keeps showing while a pushed screen hides it. */
private class LastTab {
    var tab: TopLevelDestination? = null
}

/** Navigation bars (rather than rails or drawers) are what compact windows get; Pingogram floats them. */
private fun NavigationSuiteType.isNavigationBar(): Boolean =
    this == NavigationSuiteType.ShortNavigationBarCompact ||
        this == NavigationSuiteType.ShortNavigationBarMedium ||
        this == NavigationSuiteType.NavigationBar

/**
 * The rail's primary action: global search, as the floating bar's detached button is in compact windows. The rail's
 * header places it at its start edge, so it brings its own start padding, as in Material's rail samples.
 */
@Composable
private fun SearchButton(onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick, modifier = Modifier.padding(start = RAIL_HEADER_START_PADDING)) {
        Icon(
            painter = painterResource(UiR.drawable.ic_search),
            contentDescription = stringResource(R.string.nav_search),
        )
    }
}

/** Centers a 56 dp FAB over the items of the 96 dp wide collapsed rail; Material has no token for it. */
private val RAIL_HEADER_START_PADDING = 20.dp

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
