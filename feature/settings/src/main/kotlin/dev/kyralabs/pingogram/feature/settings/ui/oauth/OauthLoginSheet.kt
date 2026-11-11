package dev.kyralabs.pingogram.feature.settings.ui.oauth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.qr.StepBadge
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The destination of a "Log in with Telegram" request (`OauthLoginRoute`): [OauthLoginSheet] for its
 * [OauthLoginViewModel], over the screen it was opened from. The website opens in the browser once logged in, with a
 * `Confirm` haptic; closing the sheet without answering declines the request. [onClose] leaves the destination.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OauthLoginRoute(
    onClose: () -> Unit,
    viewModel: OauthLoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    val currentOnClose by rememberUpdatedState(onClose)
    val close = { scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnClose() } }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is OauthLoginEffect.OpenUrl -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    runCatching { uriHandler.openUri(effect.url) }
                }

                OauthLoginEffect.Close -> {
                    close()
                }
            }
        }
    }
    OauthLoginSheet(
        state = state,
        onEvent = viewModel::onEvent,
        onClose = { close() },
        onDismiss = {
            viewModel.onDismissed()
            currentOnClose()
        },
        sheetState = sheetState,
    )
}

/** The sheet (`docs/DESIGN_SYSTEM.md` §6, "Log in with Telegram"): a `ModalBottomSheet` that opens expanded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OauthLoginSheet(
    state: OauthLoginUiState,
    onEvent: (OauthLoginEvent) -> Unit,
    onClose: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        OauthLoginContent(state, onEvent, onClose)
    }
}

/**
 * What the sheet shows, its steps crossfading on the effects springs as it resizes on the default spatial spring:
 * loading, the code to pick, what is shared, done, or why it failed.
 */
@Composable
internal fun OauthLoginContent(
    state: OauthLoginUiState,
    onEvent: (OauthLoginEvent) -> Unit,
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
        contentKey = { it.step },
        contentAlignment = Alignment.TopCenter,
        label = "oauthLoginStep",
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
            when (step.step) {
                OauthLoginStep.Loading -> Loading()
                OauthLoginStep.Failed -> Failed(step, onEvent, onClose)
                OauthLoginStep.MatchCode -> MatchCode(step, onEvent)
                OauthLoginStep.Confirm -> Confirm(step, onEvent)
                OauthLoginStep.Done -> Done(step, onClose)
            }
        }
    }
}

@Composable
private fun Loading() {
    val description = stringResource(R.string.settings_oauth_loading)
    Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.extraLarge), contentAlignment = Alignment.Center) {
        DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
    }
}

/** The step's title, a heading, centred, over [text] where there is one. */
@Composable
private fun Title(
    title: String,
    text: String? = null,
) {
    Text(
        text = title,
        modifier = Modifier.semantics { heading() },
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleLargeEmphasized,
    )
    text?.let {
        Text(
            text = it,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** The request could not be read or the code was wrong: why, "Try again" where trying again may help, "Close". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Failed(
    state: OauthLoginUiState,
    onEvent: (OauthLoginEvent) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val badge = MaterialTheme.sizes.headlineBadge
    Box(
        Modifier.size(badge).background(colors.errorContainer, MaterialShapes.Cookie4Sided.toShape()),
        Alignment.Center,
    ) {
        Icon(
            painterResource(UiR.drawable.ic_error),
            contentDescription = null,
            modifier = Modifier.size(badge / 2),
            tint = colors.onErrorContainer,
        )
    }
    val reason = state.error?.let { errorMessage(LocalResources.current, it, isDebuggable()) }
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Title(stringResource(R.string.settings_oauth_failed), reason?.takeIf { it.isNotEmpty() })
    }
    Actions(
        secondary = if (state.request == null) R.string.settings_oauth_close else null,
        onSecondary = onClose,
        primary = if (state.request == null) R.string.settings_oauth_try_again else R.string.settings_oauth_close,
        onPrimary = { if (state.request == null) onEvent(OauthLoginEvent.Retry) else onClose() },
        enabled = true,
    )
}

/** The code the website shows, picked among Telegram's codes; a wrong one ends the request. */
@Composable
private fun MatchCode(
    state: OauthLoginUiState,
    onEvent: (OauthLoginEvent) -> Unit,
) {
    val request = state.request ?: return
    StepBadge(UiR.drawable.ic_public)
    Title(
        stringResource(R.string.settings_oauth_code_title, request.domain.bidiIsolated()).bidiIsolated(),
        stringResource(R.string.settings_oauth_code_text),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        request.matchCodes.forEach { code ->
            FilledTonalButton(
                onClick = { onEvent(OauthLoginEvent.PickCode(code)) },
                enabled = !state.isBusy,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(code, style = MaterialTheme.typography.titleLarge.userContent)
            }
        }
    }
    Progress(state.isBusy)
    Actions(
        secondary = null,
        primary = R.string.settings_oauth_decline,
        onPrimary = { onEvent(OauthLoginEvent.Decline) },
        enabled = !state.isBusy,
        primaryIsText = true,
    )
}

/**
 * What logging in shares: who asks and from where, the switches for what the website asks for (off at first), the TON
 * wallet that is never shared, then "Decline" and "Log in".
 */
@Composable
private fun Confirm(
    state: OauthLoginUiState,
    onEvent: (OauthLoginEvent) -> Unit,
) {
    val request = state.request ?: return
    val domain = request.domain.bidiIsolated()
    val name = request.appName.ifBlank { stringResource(R.string.settings_oauth_unverified) }
    val source = if (request.isApp) R.string.settings_oauth_app else R.string.settings_oauth_website
    StepBadge(UiR.drawable.ic_public)
    Title(
        stringResource(R.string.settings_oauth_title, domain).bidiIsolated(),
        listOfNotNull(
            stringResource(source, name.bidiIsolated()).bidiIsolated(),
            state.accountName?.let { stringResource(R.string.settings_oauth_as, it.bidiIsolated()).bidiIsolated() },
        ).joinToString("\n"),
    )
    Details(state)
    Toggles(state, onEvent)
    if (request.asksForTonWallet) {
        Text(
            text = stringResource(R.string.settings_oauth_ton),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    state.error?.let { error ->
        val text = errorMessage(LocalResources.current, error, isDebuggable())
        if (text.isNotEmpty()) {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Progress(state.isBusy)
    Actions(
        secondary = R.string.settings_oauth_decline,
        onSecondary = { onEvent(OauthLoginEvent.Decline) },
        primary = R.string.settings_oauth_log_in,
        onPrimary = { onEvent(OauthLoginEvent.LogIn) },
        enabled = !state.isBusy,
    )
}

/** Logged in: the badge with a check, that the website opened to go on, and "Done". */
@Composable
private fun ColumnScope.Done(
    state: OauthLoginUiState,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        StepBadge(UiR.drawable.ic_check)
        Title(
            stringResource(
                R.string.settings_oauth_done,
                state.request
                    ?.domain
                    .orEmpty()
                    .bidiIsolated(),
            ).bidiIsolated(),
            stringResource(R.string.settings_oauth_done_text),
        )
    }
    Button(onClick = onClose, modifier = Modifier.align(Alignment.End), shapes = ButtonDefaults.shapes()) {
        Text(stringResource(R.string.settings_oauth_close), style = MaterialTheme.typography.labelLargeEmphasized)
    }
}

/** A `TextButton` [secondary] at the start where there is one, the [primary] action at the end. */
@Suppress("LongParameterList") // The two buttons' texts and actions.
@Composable
private fun Actions(
    secondary: Int?,
    primary: Int,
    onPrimary: () -> Unit,
    enabled: Boolean,
    onSecondary: () -> Unit = {},
    primaryIsText: Boolean = false,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (secondary != null) Arrangement.SpaceBetween else Arrangement.End,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        secondary?.let {
            TextButton(onClick = onSecondary, enabled = enabled, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(it), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (primaryIsText) {
            TextButton(onClick = onPrimary, enabled = enabled, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(primary), style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Button(onClick = onPrimary, enabled = enabled, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(primary), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}
