package dev.kyralabs.pingogram.feature.scanner.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.domain.model.LinkTarget
import dev.kyralabs.pingogram.core.navigation.QrScannerRoute
import dev.kyralabs.pingogram.feature.scanner.ScanPurpose
import dev.kyralabs.pingogram.feature.scanner.QrScannerRoute as QrScannerScreenRoute

/**
 * Adds the QR code scanner, [QrScannerRoute] (`REQUIREMENTS.md` FR-P5), to the graph: full screen above the screen it
 * is opened from. [onBack] closes it; [onOpen] gets the target of a Telegram link it read, to close it and open the
 * target's screen in its place (`openLinkTarget`).
 */
fun NavGraphBuilder.qrScannerScreen(
    onBack: () -> Unit,
    onOpen: (LinkTarget) -> Unit,
) {
    composable<QrScannerRoute> { QrScannerScreenRoute(onBack = onBack, onOpen = onOpen) }
}

/** Opens the QR code scanner for [purpose], unless it shows already. */
fun NavController.navigateToQrScanner(purpose: ScanPurpose = ScanPurpose.Any) =
    navigate(QrScannerRoute(purpose.name)) { launchSingleTop = true }
