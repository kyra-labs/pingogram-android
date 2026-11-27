package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem

/** "Switch to a working proxy" (`REQUIREMENTS.md` FR-X27), off by default: what it does in its summary. */
@Composable
internal fun AutoSwitchItem(
    on: Boolean,
    shapes: ListItemShapes,
    onEvent: (ProxiesEvent) -> Unit,
) {
    SwitchItem(
        title = stringResource(R.string.settings_proxy_auto_switch),
        summary = stringResource(R.string.settings_proxy_auto_switch_summary),
        checked = on,
        shapes = shapes,
        onCheckedChange = { onEvent(ProxiesEvent.SetAutoSwitch(it)) },
    )
}
