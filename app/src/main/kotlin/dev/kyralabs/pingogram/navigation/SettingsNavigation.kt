package dev.kyralabs.pingogram.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dev.kyralabs.pingogram.feature.calls.navigation.recentCallsScreen
import dev.kyralabs.pingogram.feature.scanner.ScanPurpose
import dev.kyralabs.pingogram.feature.scanner.navigation.navigateToQrScanner
import dev.kyralabs.pingogram.feature.settings.navigation.popBackStackAfterProfileSaved
import dev.kyralabs.pingogram.feature.settings.navigation.popBackStackAfterTwoStepVerification
import dev.kyralabs.pingogram.feature.settings.navigation.settingsPages
import dev.kyralabs.pingogram.feature.settings.navigation.settingsScreen

/**
 * The Settings tab and its pages, pushed above the tab on every window, as the archive is, and Recent calls, which
 * Settings home opens too.
 */
internal fun NavGraphBuilder.settings(navController: NavHostController) {
    val open = { route: Any -> navController.navigate(route) { launchSingleTop = true } }
    val scanQrCode = { forDeviceLogin: Boolean ->
        navController.navigateToQrScanner(if (forDeviceLogin) ScanPurpose.DeviceLogin else ScanPurpose.Any)
    }
    settingsScreen(onOpen = open, onScanQrCode = scanQrCode)
    settingsPages(
        onOpen = open,
        onBack = { navController.popBackStack() },
        onProfileSaved = { navController.popBackStackAfterProfileSaved() },
        onTwoStepVerificationDone = { navController.popBackStackAfterTwoStepVerification(it) },
        onScanQrCode = scanQrCode,
    )
    // Recent calls, from Settings home and the Contacts tab (LocalRecentCallsLauncher, provided by PingogramApp).
    recentCallsScreen(onBack = { navController.popBackStack() })
}
