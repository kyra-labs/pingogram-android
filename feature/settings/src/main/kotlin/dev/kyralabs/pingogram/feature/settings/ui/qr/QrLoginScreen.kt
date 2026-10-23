package dev.kyralabs.pingogram.feature.settings.ui.qr

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
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
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.SessionDevice
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SessionItem
import dev.kyralabs.pingogram.feature.settings.ui.SessionRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The page that logs in another device by its QR code (`QrLoginRoute`): hoists [QrLoginViewModel], with a `Confirm`
 * haptic once Telegram took the code and a `Reject` one when it did not. [onBack] leaves the page, also from "Cancel"
 * and "Done"; Active sessions below it loads its list again as it shows.
 */
@Composable
internal fun QrLoginRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QrLoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            haptics.performHapticFeedback(
                when (effect) {
                    QrLoginEffect.Confirmed -> HapticFeedbackType.Confirm
                    QrLoginEffect.Rejected -> HapticFeedbackType.Reject
                },
            )
        }
    }
    QrLoginScreen(state, viewModel::onEvent, onBack, modifier)
}

/**
 * Linking a desktop device (`docs/DESIGN_SYSTEM.md` §6 "Link a device"), a settings page in two steps that crossfade
 * on the effects springs as the page resizes on the default spatial spring:
 *
 * - **The question**, before anything is sent: a `MaterialShapes.Cookie9Sided` badge with a computer, "Log in on
 *   another device?" in `titleLargeEmphasized`, that only codes from Telegram apps on the user's own devices may be
 *   scanned, in `bodyMedium` `onSurfaceVariant`, why the last try failed in `error`, a `LinearWavyProgressIndicator`
 *   while Telegram answers, then "Cancel" (`TextButton`) at the start and "Log in" (default `Button`) at the end.
 * - **Done**: the new session as Active sessions shows it, that it can be logged out there, and "Done".
 */
@Composable
internal fun QrLoginScreen(
    state: QrLoginUiState,
    onEvent: (QrLoginEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    SettingsPageScaffold(
        title = stringResource(R.string.settings_qr_login_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec()))
                    .using(SizeTransform(clip = false) { _, _ -> motionScheme.defaultSpatialSpec() })
            },
            contentKey = { it::class },
            label = "qrLoginStep",
        ) { step ->
            when (step) {
                is QrLoginUiState.Confirm -> ConfirmStep(step, onEvent, onBack)
                is QrLoginUiState.Done -> DoneStep(step.session, onBack)
            }
        }
    }
}

@Composable
private fun ConfirmStep(
    state: QrLoginUiState.Confirm,
    onEvent: (QrLoginEvent) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        StepBadge(R.drawable.ic_computer)
        Text(
            text = stringResource(R.string.settings_qr_login_question),
            modifier = Modifier.semantics { heading() },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        Text(
            text = stringResource(R.string.settings_qr_login_warning),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        state.error?.let { error ->
            val text = errorMessage(LocalResources.current, error, isDebuggable())
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (state.isConfirming) {
            val busy = stringResource(R.string.settings_qr_login_busy)
            LinearWavyProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = busy })
        }
        StepActions(
            primary = R.string.settings_qr_login_confirm,
            onPrimary = { onEvent(QrLoginEvent.Confirm) },
            enabled = !state.isConfirming,
            secondary = R.string.settings_qr_login_cancel,
            onSecondary = onCancel,
        )
    }
}

@Composable
private fun DoneStep(
    session: SessionItem,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        Column(
            modifier =
                Modifier.fillMaxWidth().semantics(
                    mergeDescendants = true,
                ) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            StepBadge(UiR.drawable.ic_check)
            Text(
                text = stringResource(R.string.settings_qr_login_done),
                modifier = Modifier.semantics { heading() },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        }
        SettingsGroup(
            title = stringResource(R.string.settings_qr_login_new_session),
            footer = stringResource(R.string.settings_qr_login_done_note),
        ) {
            item(key = session.id) { shapes -> SessionRow(session, isBusy = false, shapes = shapes) }
        }
        StepActions(primary = R.string.settings_qr_login_finish, onPrimary = onDone, enabled = true)
    }
}

/** [icon] on a `MaterialShapes.Cookie9Sided` of `primaryContainer`, decorative, as the badges of auth steps. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepBadge(icon: Int) {
    val badgeSize = MaterialTheme.sizes.headlineBadge
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(badgeSize).background(colors.primaryContainer, MaterialShapes.Cookie9Sided.toShape()),
        Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(badgeSize / 2),
            tint = colors.onPrimaryContainer,
        )
    }
}

/** A `TextButton` [secondary] at the start where there is one, a default `Button` [primary] at the end. */
@Composable
private fun StepActions(
    @StringRes primary: Int,
    onPrimary: () -> Unit,
    enabled: Boolean,
    @StringRes secondary: Int? = null,
    onSecondary: () -> Unit = {},
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (secondary != null) Arrangement.SpaceBetween else Arrangement.End,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (secondary != null) {
            TextButton(onClick = onSecondary, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(secondary), style = MaterialTheme.typography.labelLarge)
            }
        }
        Button(onClick = onPrimary, enabled = enabled, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(primary), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/** A Mac logged in from Berlin a moment ago. */
internal val PreviewLinkedSession =
    SessionItem(
        id = 7,
        device = SessionDevice.Desktop,
        title = UiText.Plain("MacBook Pro"),
        app = UiText.Plain("Telegram Desktop 6.2 · macOS 16"),
        activity = UiText.Plain("Berlin, Germany · 9:41 AM"),
    )

@PingogramPreviews
@Composable
private fun QrLoginScreenPreview() {
    PingogramPreview { QrLoginScreen(QrLoginUiState.Confirm(), {}, {}) }
}

@PingogramPreviews
@Composable
private fun QrLoginScreenExpiredPreview() {
    PingogramPreview { QrLoginScreen(QrLoginUiState.Confirm(error = DomainError.QrLoginExpired), {}, {}) }
}

@PingogramPreviews
@Composable
private fun QrLoginScreenDonePreview() {
    PingogramPreview { QrLoginScreen(QrLoginUiState.Done(PreviewLinkedSession), {}, {}) }
}
