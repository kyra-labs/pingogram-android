package dev.kyralabs.pingogram.feature.settings.ui.twostep

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialog
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialogTexts
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationFlowEvent as Event

/**
 * A flow of 2-step verification: hoists its [TwoStepVerificationFlowViewModel], leaves with [onFinished] once it is
 * done (with a confirming buzz), buzzes when Telegram rejects what was entered, and shows the other errors in
 * snackbars. [onBack] leaves the flow from its first step.
 */
@Composable
internal fun TwoStepVerificationFlowRoute(
    flow: TwoStepVerificationFlow,
    onFinished: (TwoStepVerificationResult) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TwoStepVerificationFlowViewModel =
        hiltViewModel<TwoStepVerificationFlowViewModel, TwoStepVerificationFlowViewModel.Factory>(
            key = "two-step-$flow",
        ) { it.create(flow) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TwoStepVerificationFlowEffect.Finished -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onFinished(effect.result)
                }

                TwoStepVerificationFlowEffect.InputRejected -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                }

                // Snackbars queue up; later effects must not wait for them.
                is TwoStepVerificationFlowEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }

                TwoStepVerificationFlowEffect.CodeResent -> {
                    launch {
                        snackbarHostState.showSnackbar(
                            resources.getString(R.string.settings_two_step_code_resent),
                        )
                    }
                }

                is TwoStepVerificationFlowEffect.ResetDeclined -> {
                    launch {
                        snackbarHostState.showSnackbar(
                            resources.getString(R.string.settings_two_step_reset_declined, effect.retryDate),
                        )
                    }
                }
            }
        }
    }
    TwoStepVerificationFlowScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * A flow of 2-step verification, one step at a time on a settings page (`docs/DESIGN_SYSTEM.md` §6, "Two-step
 * verification"): what the step asks for in `bodyMedium`, its rounded tonal fields, the countdown of a `FLOOD_WAIT`, a
 * wavy progress indicator while a request is on its way, then the secondary action as a [TextButton] at the start and
 * the main one as a [Button] at the end. Steps crossfade on the effects springs while the page resizes on the default
 * spatial spring. Back, from the top bar or the system, returns to the step before while there is one.
 */
@Composable
internal fun TwoStepVerificationFlowScreen(
    state: TwoStepVerificationFlowUiState,
    onEvent: (Event) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    BackHandler(enabled = state.canGoBack) { onEvent(Event.Back) }
    val motionScheme = MaterialTheme.motionScheme
    SettingsPageScaffold(
        title = stringResource(state.titleRes()),
        onBack = { if (state.canGoBack) onEvent(Event.Back) else onBack() },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) {
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                    fadeOut(motionScheme.fastEffectsSpec()) using
                    SizeTransform(clip = false) { _, _ -> motionScheme.defaultSpatialSpec() }
            },
            label = "TwoStepVerificationStep",
        ) { step ->
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                when (step) {
                    TwoStepVerificationStep.CurrentPassword -> CurrentPasswordStep(state, onEvent)
                    TwoStepVerificationStep.NewPassword -> NewPasswordStep(state, onEvent)
                    TwoStepVerificationStep.Hint -> HintStep(state, onEvent)
                    TwoStepVerificationStep.RecoveryEmail -> RecoveryEmailStep(state, onEvent)
                    TwoStepVerificationStep.EmailCode -> CodeStep(state, recovery = false, onEvent)
                    TwoStepVerificationStep.RecoveryCode -> CodeStep(state, recovery = true, onEvent)
                    TwoStepVerificationStep.Reset -> ResetStep(state, onEvent)
                }
            }
        }
    }
}

private fun TwoStepVerificationFlowUiState.titleRes(): Int =
    when {
        recovering || step == TwoStepVerificationStep.RecoveryCode || step == TwoStepVerificationStep.Reset -> {
            R.string.settings_two_step_title_forgot
        }

        else -> {
            when (flow) {
                TwoStepVerificationFlow.SetPassword -> R.string.settings_two_step_set_password

                TwoStepVerificationFlow.ChangePassword -> R.string.settings_two_step_change_password

                TwoStepVerificationFlow.RemovePassword -> R.string.settings_two_step_turn_off_title_short

                TwoStepVerificationFlow.ChangeRecoveryEmail,
                TwoStepVerificationFlow.ConfirmRecoveryEmail,
                -> R.string.settings_two_step_recovery_email
            }
        }
    }

@PingogramPreviews
@Composable
private fun TwoStepVerificationFlowCurrentPasswordPreview() {
    FlowPreview(TwoStepVerificationFlow.ChangePassword, TwoStepVerificationStep.CurrentPassword)
}

@PingogramPreviews
@Composable
private fun TwoStepVerificationFlowNewPasswordPreview() {
    FlowPreview(TwoStepVerificationFlow.SetPassword, TwoStepVerificationStep.NewPassword)
}

@PingogramPreviews
@Composable
private fun TwoStepVerificationFlowRecoveryEmailPreview() {
    FlowPreview(TwoStepVerificationFlow.SetPassword, TwoStepVerificationStep.RecoveryEmail)
}

@PingogramPreviews
@Composable
private fun TwoStepVerificationFlowEmailCodePreview() {
    FlowPreview(TwoStepVerificationFlow.ConfirmRecoveryEmail, TwoStepVerificationStep.EmailCode)
}

@PingogramPreviews
@Composable
private fun TwoStepVerificationFlowResetPreview() {
    FlowPreview(TwoStepVerificationFlow.ChangePassword, TwoStepVerificationStep.Reset)
}

@PingogramPreviews
@Composable
private fun TwoStepVerificationFlowFloodWaitPreview() {
    PingogramPreview {
        TwoStepVerificationFlowScreen(
            state =
                SettingsPreviewData
                    .twoStepFlow(TwoStepVerificationFlow.ChangePassword, TwoStepVerificationStep.CurrentPassword)
                    .copy(floodWaitSeconds = 42, floodWaitTotalSeconds = 60),
            onEvent = {},
            onBack = {},
        )
    }
}

@Composable
private fun FlowPreview(
    flow: TwoStepVerificationFlow,
    step: TwoStepVerificationStep,
) {
    PingogramPreview {
        TwoStepVerificationFlowScreen(state = SettingsPreviewData.twoStepFlow(flow, step), onEvent = {}, onBack = {})
    }
}
