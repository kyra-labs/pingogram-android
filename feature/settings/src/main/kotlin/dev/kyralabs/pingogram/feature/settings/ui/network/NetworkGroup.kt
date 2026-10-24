package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.model.SavedProxy
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.PageItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup

/** "Network": the proxy in use, a row that opens the proxies ([onOpenProxies]), and network usage. */
@Composable
internal fun NetworkGroup(
    proxy: SavedProxy?,
    onOpenProxies: () -> Unit,
    onOpenNetworkUsage: () -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_section_network)) {
        item { shapes ->
            PageItem(
                title = stringResource(R.string.settings_proxy),
                summary = proxySummary(proxy),
                icon = R.drawable.ic_vpn_lock,
                shapes = shapes,
                onClick = onOpenProxies,
            )
        }
        item { shapes ->
            PageItem(
                title = stringResource(R.string.settings_network_usage),
                summary = stringResource(R.string.settings_network_usage_summary),
                icon = R.drawable.ic_data_usage,
                shapes = shapes,
                onClick = onOpenNetworkUsage,
            )
        }
    }
}

/** "Connected through Work" or "Off · Connect directly". */
@Composable
private fun proxySummary(proxy: SavedProxy?): String =
    if (proxy == null) {
        stringResource(R.string.settings_proxy_summary_off)
    } else {
        stringResource(R.string.settings_proxy_summary_on, proxyTitle(proxy).bidiIsolated())
    }
