package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.qr.StepBadge
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The destination of a proxy link (`ProxyLinkRoute`): [ProxyLinkSheet] for its [ProxyLinkViewModel], over the screen
 * the link was opened from, with a `Confirm` haptic once the proxy is on. [onClose] leaves the destination once the
 * sheet closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProxyLinkRoute(
    onClose: () -> Unit,
    viewModel: ProxyLinkViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(state.connected) {
        if (state.connected) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    ProxyLinkSheet(
        state = state,
        onEvent = viewModel::onEvent,
        onClose = { scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnClose() } },
        onDismiss = { currentOnClose() },
        sheetState = sheetState,
    )
}

/** The sheet of a proxy link (`docs/DESIGN_SYSTEM.md` §6, "Proxy link sheet"): a `ModalBottomSheet`, expanded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProxyLinkSheet(
    state: ProxyLinkUiState,
    onEvent: (ProxyLinkEvent) -> Unit,
    onClose: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ProxyLinkContent(state, onEvent, onClose)
    }
}

/**
 * What the sheet shows: the question with the proxy's details, or that the proxy is on; the two crossfade on the
 * effects springs while the sheet resizes on the default spatial spring.
 */
@Composable
internal fun ProxyLinkContent(
    state: ProxyLinkUiState,
    onEvent: (ProxyLinkEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = state,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            (fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec()))
                .using(SizeTransform(clip = false) { _, _ -> motionScheme.defaultSpatialSpec() })
        },
        contentKey = { it.connected },
        contentAlignment = Alignment.TopCenter,
        label = "proxyLinkStep",
    ) { step ->
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.large)
                .padding(bottom = MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            if (step.connected) Connected(onClose) else Question(step, onEvent, onClose)
        }
    }
}

/** The question, the proxy's details, why connecting failed, the progress, then "Cancel" and "Connect". */
@Composable
private fun Question(
    state: ProxyLinkUiState,
    onEvent: (ProxyLinkEvent) -> Unit,
    onClose: () -> Unit,
) {
    StepBadge(R.drawable.ic_vpn_lock)
    Text(
        text = stringResource(R.string.settings_proxy_link_title),
        modifier = Modifier.semantics { heading() },
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleLargeEmphasized,
    )
    Text(
        text = stringResource(R.string.settings_proxy_link_text),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
    ProxyDetails(state, onEvent)
    if (state.inUse) {
        Text(
            text = stringResource(R.string.settings_proxy_link_in_use),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    state.error?.let { ConnectError(it) }
    if (state.connecting) {
        val connecting = stringResource(R.string.settings_proxy_link_connecting)
        LinearWavyProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = connecting })
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClose, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.settings_proxy_link_cancel), style = MaterialTheme.typography.labelLarge)
        }
        Button(
            onClick = { onEvent(ProxyLinkEvent.Connect) },
            enabled = !state.connecting && !state.inUse,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(
                stringResource(R.string.settings_proxy_link_connect),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}

/**
 * The proxy as two rows on the sheet's color: the server and port (left to right) over the protocol, and how it
 * answered, a polite live region; neither credentials nor the secret show.
 */
@Composable
private fun ProxyDetails(
    state: ProxyLinkUiState,
    onEvent: (ProxyLinkEvent) -> Unit,
) {
    val proxy = state.proxy
    val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    Column(Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(R.string.settings_proxy_address, proxy.server, proxy.port.toString()),
                    style = MaterialTheme.typography.bodyLarge.numericContent,
                )
            },
            supportingContent = { Text(proxyKindName(proxy.type.kind)) },
            colors = colors,
        )
        ListItem(
            headlineContent = { Text(proxyCheckText(state.check)) },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            colors = colors,
            trailingContent = {
                if (state.check != ProxyCheck.Checking) {
                    TextButton(onClick = { onEvent(ProxyLinkEvent.CheckAgain) }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.settings_proxy_check_again))
                    }
                }
            },
        )
    }
}

/** Why connecting failed, in `error`, said by screen readers as it appears. */
@Composable
private fun ConnectError(error: DomainError) {
    val text = errorMessage(LocalResources.current, error, isDebuggable())
    if (text.isEmpty()) return
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** The proxy is on: the badge with a check, what that means and where to turn it off, and "Done". */
@Composable
private fun ColumnScope.Connected(onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        StepBadge(UiR.drawable.ic_check)
        Text(
            text = stringResource(R.string.settings_proxy_link_connected),
            modifier = Modifier.semantics { heading() },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        Text(
            text = stringResource(R.string.settings_proxy_link_connected_text),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Button(onClick = onClose, modifier = Modifier.align(Alignment.End), shapes = ButtonDefaults.shapes()) {
        Text(stringResource(R.string.settings_proxy_link_done), style = MaterialTheme.typography.labelLargeEmphasized)
    }
}
