package dev.kyralabs.pingogram.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.dialog
import dev.kyralabs.pingogram.core.navigation.EditProxyRoute
import dev.kyralabs.pingogram.core.navigation.NetworkUsageRoute
import dev.kyralabs.pingogram.core.navigation.ProxiesRoute
import dev.kyralabs.pingogram.core.navigation.ProxyLinkRoute
import dev.kyralabs.pingogram.core.ui.component.NoDialogWindowDim
import dev.kyralabs.pingogram.core.ui.component.SheetDialogProperties
import dev.kyralabs.pingogram.feature.settings.ui.network.EditProxyRoute as EditProxyScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.network.NetworkUsageRoute as NetworkUsageScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.network.ProxiesRoute as ProxiesScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.network.ProxyLinkRoute as ProxyLinkSheetRoute

/**
 * The network's pages above Data and storage: network usage, the proxies (`docs/adr/0027-proxies.md`), the page that
 * adds or edits one above them, and the sheet of a proxy link (a dialog destination, which links open from anywhere).
 */
internal fun NavGraphBuilder.networkPages(
    onOpen: (route: Any) -> Unit,
    onBack: () -> Unit,
) {
    settingsPage<ProxiesRoute> {
        ProxiesScreenRoute(
            onAddProxy = { onOpen(EditProxyRoute()) },
            onEditProxy = { onOpen(EditProxyRoute(it)) },
            onBack = onBack,
        )
    }
    settingsPage<EditProxyRoute> { EditProxyScreenRoute(onBack = onBack) }
    settingsPage<NetworkUsageRoute> { NetworkUsageScreenRoute(onBack = onBack) }
    // A dialog destination, so the screen below stays visible under the sheet's scrim.
    dialog<ProxyLinkRoute>(dialogProperties = SheetDialogProperties) {
        NoDialogWindowDim()
        ProxyLinkSheetRoute(onClose = onBack)
    }
}
