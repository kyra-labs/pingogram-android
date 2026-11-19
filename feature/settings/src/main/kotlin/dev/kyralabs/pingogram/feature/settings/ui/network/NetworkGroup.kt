package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import dev.kyralabs.pingogram.core.domain.model.SavedProxy
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.PageItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSplitRow

/**
 * "Network": the proxy in use, a row that opens the proxies ([ProxyRowActions.onOpen]), with "Use proxy" as its switch
 * once a proxy is saved ([SettingsSplitRow], owner decision 2026-11-19), network usage, and "Prefer IPv6" as a switch
 * row once it is read ([prefersIpv6]).
 */
@Composable
internal fun NetworkGroup(
    proxy: SavedProxy?,
    proxyActions: ProxyRowActions,
    onOpenNetworkUsage: () -> Unit,
    prefersIpv6: Boolean? = null,
    onPrefersIpv6Change: (Boolean) -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    SettingsGroup(title = stringResource(R.string.settings_section_network)) {
        item { shapes ->
            val onSwitch = proxyActions.onSwitch
            if (onSwitch != null) {
                SettingsSplitRow(
                    title = stringResource(R.string.settings_proxy),
                    summary = proxySummary(proxy),
                    checked = proxy != null,
                    shapes = shapes,
                    onOpen = proxyActions.onOpen,
                    onCheckedChange = onSwitch,
                    icon = R.drawable.ic_vpn_lock,
                    switchLabel = stringResource(R.string.settings_proxy_use),
                )
            } else {
                PageItem(
                    title = stringResource(R.string.settings_proxy),
                    summary = proxySummary(proxy),
                    icon = R.drawable.ic_vpn_lock,
                    shapes = shapes,
                    onClick = proxyActions.onOpen,
                )
            }
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
        if (prefersIpv6 != null) {
            item { shapes ->
                SettingsRow(
                    title = stringResource(R.string.settings_prefer_ipv6),
                    shapes = shapes,
                    icon = R.drawable.ic_language,
                    supportingContent = { Text(stringResource(R.string.settings_prefer_ipv6_summary)) },
                    trailingContent = { CheckSwitch(checked = prefersIpv6) },
                    onClick = {
                        haptics.performToggle(!prefersIpv6)
                        onPrefersIpv6Change(!prefersIpv6)
                    },
                    modifier =
                        Modifier.semantics {
                            role = Role.Switch
                            toggleableState = ToggleableState(prefersIpv6)
                        },
                )
            }
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

/** What the Proxy row does: open the proxies' page, and turn "Use proxy" on or off ([onSwitch]; null, none saved). */
internal class ProxyRowActions(
    val onOpen: () -> Unit,
    val onSwitch: ((Boolean) -> Unit)? = null,
)
