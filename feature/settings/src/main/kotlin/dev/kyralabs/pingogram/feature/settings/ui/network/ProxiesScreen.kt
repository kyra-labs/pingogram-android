package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.ProxyKind
import dev.kyralabs.pingogram.core.domain.model.SavedProxy
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialog
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialogTexts
import dev.kyralabs.pingogram.feature.settings.ui.SettingsError
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem
import dev.kyralabs.pingogram.feature.settings.ui.settingsRowColors
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.time.Duration
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Proxy page: hoists [ProxiesViewModel], shows its errors in snackbars, and opens the page that adds a proxy
 * ([onAddProxy], also when the switch is turned on without one) or edits one ([onEditProxy]).
 */
@Composable
internal fun ProxiesRoute(
    onAddProxy: () -> Unit,
    onEditProxy: (id: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProxiesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ProxiesEffect.AddProxy -> {
                    onAddProxy()
                }

                is ProxiesEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    ProxiesScreen(state, viewModel::onEvent, onAddProxy, onEditProxy, onBack, modifier, snackbarHostState)
}

/**
 * The proxies (`docs/DESIGN_SYSTEM.md` §6, "Proxies"): "Use proxy" (a switch, while there are proxies) and "Add proxy";
 * then the proxies as radio rows, the one in use selected, each with its protocol and how it answered, and a menu
 * (Edit; Delete in `error`, which asks first); a footer says what a proxy can see. Without proxies the first group's
 * footer says what they are for.
 */
@Suppress("LongParameterList") // The page's state and events, the two pages it opens, and its scaffold's parts.
@Composable
internal fun ProxiesScreen(
    state: ProxiesUiState,
    onEvent: (ProxiesEvent) -> Unit,
    onAddProxy: () -> Unit,
    onEditProxy: (id: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_proxy),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            ProxiesUiState.Loading -> {
                SettingsLoading(stringResource(R.string.settings_proxy_loading))
            }

            is ProxiesUiState.Error -> {
                SettingsError(state.error, onRetry = { onEvent(ProxiesEvent.Retry) })
            }

            is ProxiesUiState.Content -> {
                ProxiesContent(state, onEvent, onAddProxy, onEditProxy)
            }
        }
    }
    val deleting = (state as? ProxiesUiState.Content)?.deleting
    if (deleting != null) {
        DestructiveDialog(
            icon = UiR.drawable.ic_delete,
            texts =
                DestructiveDialogTexts(
                    title = stringResource(R.string.settings_proxy_delete_title),
                    text = stringResource(R.string.settings_proxy_delete_text, proxyTitle(deleting)),
                    confirm = stringResource(R.string.settings_proxy_delete_confirm),
                ),
            onConfirm = { onEvent(ProxiesEvent.ConfirmDelete) },
            onDismiss = { onEvent(ProxiesEvent.DismissDelete) },
        )
    }
}

@Composable
private fun ProxiesContent(
    state: ProxiesUiState.Content,
    onEvent: (ProxiesEvent) -> Unit,
    onAddProxy: () -> Unit,
    onEditProxy: (id: Int) -> Unit,
) {
    // Without proxies, the footer says what they are for.
    SettingsGroup(footer = stringResource(R.string.settings_proxy_none_text).takeIf { state.proxies.isEmpty() }) {
        if (state.proxies.isNotEmpty()) {
            item { shapes ->
                SwitchItem(
                    title = stringResource(R.string.settings_proxy_use),
                    summary = stringResource(R.string.settings_proxy_use_summary),
                    checked = state.isProxyOn,
                    shapes = shapes,
                    onCheckedChange = { onEvent(ProxiesEvent.SetProxyOn(it)) },
                )
            }
        }
        item { shapes -> SettingsRow(stringResource(R.string.settings_proxy_add), shapes, onClick = onAddProxy) }
        if (state.proxies.isNotEmpty()) {
            item { shapes ->
                SettingsRow(
                    title = stringResource(R.string.settings_proxy_check_again),
                    shapes = shapes,
                    onClick = { onEvent(ProxiesEvent.CheckAgain) },
                )
            }
        }
    }
    if (state.proxies.isEmpty()) return
    SettingsGroup(
        title = stringResource(R.string.settings_proxy_section),
        footer = stringResource(R.string.settings_proxy_footer),
        rowsModifier = Modifier.selectableGroup(),
    ) {
        state.proxies.forEach { item ->
            item(key = item.proxy.id) { shapes ->
                ProxyRow(
                    item = item,
                    enabled = !state.busy,
                    shapes = shapes,
                    onUse = { onEvent(ProxiesEvent.Use(item.proxy.id)) },
                    onEdit = { onEditProxy(item.proxy.id) },
                    onDelete = { onEvent(ProxiesEvent.Delete(item.proxy.id)) },
                )
            }
        }
    }
}

/**
 * A proxy: a radio button (selected while it is in use), its name or address (left to right), its protocol and how it
 * answered, and its menu at the end.
 */
@Suppress("LongParameterList") // The row's proxy, its state and shape, and its three actions.
@Composable
private fun ProxyRow(
    item: ProxyItem,
    enabled: Boolean,
    shapes: ListItemShapes,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val proxy = item.proxy
    val title = proxyTitle(proxy)
    SegmentedListItem(
        selected = proxy.isEnabled,
        onClick = onUse,
        shapes = shapes,
        enabled = enabled,
        leadingContent = { RadioButton(selected = proxy.isEnabled, onClick = null, enabled = enabled) },
        supportingContent = { Text(proxyDetails(item)) },
        trailingContent = { ProxyMenu(title, onEdit, onDelete) },
        colors = settingsRowColors(),
    ) {
        val style = LocalTextStyle.current
        Text(title, style = if (proxy.name.isEmpty()) style.numericContent else style.userContent)
    }
}

/** A proxy's more-options button and its menu: Edit, then Delete in `error` in a group of its own. */
@Composable
private fun ProxyMenu(
    title: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painter = painterResource(UiR.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.settings_proxy_options, title),
            )
        }
        DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
                DropdownMenuItem(
                    onClick = {
                        open = false
                        onEdit()
                    },
                    text = { Text(stringResource(R.string.settings_proxy_edit)) },
                    shape = MenuDefaults.itemShape(0, 1).shape,
                )
            }
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 2)) {
                DropdownMenuItem(
                    onClick = {
                        open = false
                        onDelete()
                    },
                    text = { Text(stringResource(R.string.settings_proxy_delete)) },
                    shape = MenuDefaults.itemShape(0, 1).shape,
                    colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                )
            }
        }
    }
}

/** The proxy's name, or its address without one. */
@Composable
internal fun proxyTitle(proxy: SavedProxy): String =
    proxy.name.ifEmpty {
        stringResource(
            R.string.settings_proxy_address,
            proxy.proxy.server,
            proxy.proxy.port.toString(),
        )
    }

/** "SOCKS5 · Available · 120 ms": the protocol and how the proxy answered; with a name, its address instead first. */
@Composable
private fun proxyDetails(item: ProxyItem): String {
    val proxy = item.proxy.proxy
    val kind = proxyKindName(proxy.type.kind)
    val first =
        if (item.proxy.name.isEmpty()) {
            kind
        } else {
            stringResource(
                R.string.settings_proxy_details,
                kind,
                stringResource(R.string.settings_proxy_address, proxy.server, proxy.port.toString()),
            )
        }
    return stringResource(R.string.settings_proxy_details, first, proxyCheckText(item.check))
}

@Composable
internal fun proxyKindName(kind: ProxyKind): String =
    stringResource(
        when (kind) {
            ProxyKind.Socks5 -> R.string.settings_proxy_kind_socks5
            ProxyKind.Http -> R.string.settings_proxy_kind_http
            ProxyKind.Mtproto -> R.string.settings_proxy_kind_mtproto
        },
    )

/** "Checking…", "Available · 120 ms" or "Unavailable". */
@Composable
internal fun proxyCheckText(check: ProxyCheck): String =
    when (check) {
        ProxyCheck.Checking -> stringResource(R.string.settings_proxy_checking)
        is ProxyCheck.Available -> stringResource(R.string.settings_proxy_available, pingText(check.ping))
        ProxyCheck.Unavailable -> stringResource(R.string.settings_proxy_unavailable)
    }

@Composable
private fun pingText(ping: Duration): String {
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    return stringResource(R.string.settings_proxy_ping_ms, numbers.format(ping.inWholeMilliseconds))
}
