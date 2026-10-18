package dev.kyralabs.pingogram.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.auth.ui.AuthProgressScreen
import dev.kyralabs.pingogram.navigation.PingogramNavHost
import dev.kyralabs.pingogram.navigation.TopLevelDestination
import dev.kyralabs.pingogram.navigation.isInHierarchy
import dev.kyralabs.pingogram.navigation.isInMainGraph
import dev.kyralabs.pingogram.navigation.navigateToTopLevelDestination

/**
 * The app shell: the `NavHost` in a `NavigationSuiteScaffold` (`docs/DESIGN_SYSTEM.md` §6–7). [uiState] decides
 * between the login flow and the signed-in app; until it is known, after the splash screen gave up waiting, the shell
 * shows progress.
 *
 * Only the signed-in app has the navigation bar or rail; the login flow is full screen. The window size class picks
 * the navigation component, following Material's recommendation: a `ShortNavigationBar` in compact windows, with
 * side-by-side items when the window is also short (landscape phones, tabletop posture), and a collapsed
 * `WideNavigationRail` in medium and wider windows.
 *
 * The selected item comes from the back stack, so it survives rotation and process death along with the
 * `NavController` state, and stays right when back or a deep link changes the destination.
 */
@Composable
fun PingogramApp(
    uiState: MainActivityUiState,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val currentEntry = navController.currentBackStackEntryAsState()
    // Derived, so that only entering or leaving the main graph recomposes the shell, not every destination change.
    val showNavigation by remember(currentEntry) {
        derivedStateOf { currentEntry.value?.destination.isInMainGraph() }
    }
    val navigationSuiteType =
        if (showNavigation) {
            NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
        } else {
            NavigationSuiteType.None
        }
    NavigationSuiteScaffold(
        navigationItems = { PingogramNavigationItems(navController, navigationSuiteType) },
        modifier = modifier,
        navigationSuiteType = navigationSuiteType,
    ) {
        if (uiState == MainActivityUiState.Loading) {
            // The same progress as the login flow's first step: TDLib is still starting.
            AuthProgressScreen(isLoggingOut = false)
        } else {
            PingogramNavHost(navController, uiState)
        }
    }
}

/**
 * One item per [TopLevelDestination]. It reads the back stack itself, so a destination change
 * recomposes only the items and never the `NavHost` content.
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

// The signed-out shell is the login flow, whose screens have their own previews; it needs Hilt for its view model.

@PingogramPreviews
@Preview(name = "Expanded", device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun PingogramAppSignedInPreview() {
    PingogramPreview { PingogramApp(uiState = MainActivityUiState.SignedIn) }
}

@PingogramPreviews
@Composable
private fun PingogramAppLoadingPreview() {
    PingogramPreview { PingogramApp(uiState = MainActivityUiState.Loading) }
}
