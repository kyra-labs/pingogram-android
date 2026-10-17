package dev.kyralabs.pingogram.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.navigation.PingogramNavHost
import dev.kyralabs.pingogram.navigation.TopLevelDestination
import dev.kyralabs.pingogram.navigation.isInHierarchy
import dev.kyralabs.pingogram.navigation.navigateToTopLevelDestination

/**
 * The app shell: the top-level destinations in a `NavigationSuiteScaffold` around the `NavHost`
 * (`docs/DESIGN_SYSTEM.md` §6–7). The window size class picks the navigation component, following
 * Material's recommendation: a `ShortNavigationBar` in compact windows, with side-by-side items when the
 * window is also short (landscape phones, tabletop posture), and a collapsed `WideNavigationRail` in
 * medium and wider windows.
 *
 * The selected item comes from the back stack, so it survives rotation and process death along with
 * the `NavController` state, and stays right when back or a deep link changes the destination.
 */
@Composable
fun PingogramApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val navigationSuiteType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    NavigationSuiteScaffold(
        navigationItems = { PingogramNavigationItems(navController, navigationSuiteType) },
        modifier = modifier,
        navigationSuiteType = navigationSuiteType,
    ) {
        PingogramNavHost(navController)
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

@PingogramPreviews
@Preview(name = "Expanded", device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun PingogramAppPreview() {
    PingogramPreview { PingogramApp() }
}
