package dev.kyralabs.pingogram.feature.settings.ui.twostep

import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialog
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialogTexts
import dev.kyralabs.pingogram.feature.settings.ui.SettingsError
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.destructiveItemColors
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.launch

/**
 * The 2-step verification page: hoists [TwoStepVerificationViewModel], loads the state again whenever the page shows
 * again (back from a flow), and says in a snackbar what the flow it came back from did ([result], then
 * [onResultShown]) and what the page's own actions did. A row calls [onOpen] with the flow it starts.
 */
@Composable
internal fun TwoStepVerificationRoute(
    result: TwoStepVerificationResult?,
    onResultShown: () -> Unit,
    onOpen: (TwoStepVerificationFlow) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TwoStepVerificationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(TwoStepVerificationEvent.Refresh)
        onStopOrDispose {}
    }
    LaunchedEffect(result) {
        if (result != null) {
            onResultShown()
            snackbarHostState.showSnackbar(resources.getString(result.messageRes()))
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            // Snackbars queue up; later effects must not wait for them.
            launch {
                val message =
                    when (effect) {
                        is TwoStepVerificationEffect.ShowError -> {
                            errorMessage(resources, effect.error, showErrorDetails)
                        }

                        TwoStepVerificationEffect.RecoveryEmailCancelled -> {
                            resources.getString(R.string.settings_two_step_email_cancelled)
                        }

                        TwoStepVerificationEffect.ResetCancelled -> {
                            resources.getString(R.string.settings_two_step_reset_cancelled)
                        }

                        TwoStepVerificationEffect.ResetDone -> {
                            resources.getString(R.string.settings_two_step_done_password_removed)
                        }

                        is TwoStepVerificationEffect.ResetDeclined -> {
                            resources.getString(R.string.settings_two_step_reset_declined, effect.retryDate)
                        }
                    }
                snackbarHostState.showSnackbar(message)
            }
        }
    }
    TwoStepVerificationScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpen = onOpen,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * 2-step verification (`REQUIREMENTS.md` FR-S1):
 * - Off: "Set password", and what 2-step verification does.
 * - On: "Change password" and "Turn off" (behind a destructive confirmation), then changing or adding the recovery
 *   email, with whether one is set.
 * - A recovery email waiting for its code: "Enter the code" and "Don't use this address" in place of the recovery email
 *   (and with no password yet, in place of "Set password": the password waits for it).
 * - A reset of the password that waits: when it can complete, "Reset password now" (behind a destructive
 *   confirmation) once that time has passed, and "Cancel reset".
 */
@Composable
internal fun TwoStepVerificationScreen(
    state: TwoStepVerificationUiState,
    onEvent: (TwoStepVerificationEvent) -> Unit,
    onOpen: (TwoStepVerificationFlow) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_two_step_verification),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            TwoStepVerificationUiState.Loading -> {
                SettingsLoading(stringResource(R.string.settings_two_step_loading))
            }

            is TwoStepVerificationUiState.Error -> {
                SettingsError(state.error, onRetry = { onEvent(TwoStepVerificationEvent.Retry) })
            }

            is TwoStepVerificationUiState.Content -> {
                TwoStepVerificationContent(state, onEvent, onOpen)
            }
        }
    }
}

@Composable
private fun TwoStepVerificationContent(
    state: TwoStepVerificationUiState.Content,
    onEvent: (TwoStepVerificationEvent) -> Unit,
    onOpen: (TwoStepVerificationFlow) -> Unit,
) {
    var confirmTurnOff by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val enabled = !state.isWorking
    val pendingEmail = state.pendingEmailPattern
    when {
        state.hasPassword -> PasswordGroup(enabled, onOpen, onTurnOff = { confirmTurnOff = true })
        pendingEmail == null -> SetPasswordGroup(enabled, onOpen)
    }
    when {
        pendingEmail != null -> {
            PendingEmailGroup(
                pendingEmail,
                hasPassword = state.hasPassword,
                enabled,
                onEvent,
                onOpen,
            )
        }

        state.hasPassword -> {
            RecoveryEmailGroup(state.hasRecoveryEmail, enabled, onOpen)
        }
    }
    state.pendingReset?.let { reset ->
        PendingResetGroup(reset, enabled, onEvent, onCompleteReset = { confirmReset = true })
    }
    if (confirmTurnOff) {
        TurnOffDialog(
            onConfirm = {
                confirmTurnOff = false
                onOpen(TwoStepVerificationFlow.RemovePassword)
            },
            onDismiss = { confirmTurnOff = false },
        )
    }
    if (confirmReset) {
        CompleteResetDialog(
            onConfirm = {
                confirmReset = false
                onEvent(TwoStepVerificationEvent.CompleteReset)
            },
            onDismiss = { confirmReset = false },
        )
    }
}

/** The snackbar for what a flow did. */
private fun TwoStepVerificationResult.messageRes(): Int =
    when (this) {
        TwoStepVerificationResult.PasswordSet -> R.string.settings_two_step_done_password_set
        TwoStepVerificationResult.PasswordChanged -> R.string.settings_two_step_done_password_changed
        TwoStepVerificationResult.PasswordRemoved -> R.string.settings_two_step_done_password_removed
        TwoStepVerificationResult.RecoveryEmailConfirmed -> R.string.settings_two_step_done_email_confirmed
        TwoStepVerificationResult.PasswordRecovered -> R.string.settings_two_step_done_password_recovered
        TwoStepVerificationResult.ResetRequested -> R.string.settings_two_step_done_reset_requested
        TwoStepVerificationResult.ResetDone -> R.string.settings_two_step_done_password_removed
    }

@PingogramPreviews
@Composable
private fun TwoStepVerificationScreenOnPreview() {
    PingogramPreview {
        TwoStepVerificationScreen(
            SettingsPreviewData.twoStepOn,
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun TwoStepVerificationScreenOffPreview() {
    PingogramPreview {
        TwoStepVerificationScreen(
            SettingsPreviewData.twoStepOff,
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun TwoStepVerificationScreenPendingPreview() {
    PingogramPreview {
        TwoStepVerificationScreen(SettingsPreviewData.twoStepPending, onEvent = {}, onOpen = {}, onBack = {})
    }
}

@Preview(name = "Expanded", widthDp = 840, heightDp = 891)
@Composable
private fun TwoStepVerificationScreenExpandedPreview() {
    PingogramPreview {
        TwoStepVerificationScreen(
            SettingsPreviewData.twoStepOn,
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun TwoStepVerificationScreenLoadingPreview() {
    PingogramPreview {
        TwoStepVerificationScreen(TwoStepVerificationUiState.Loading, onEvent = {}, onOpen = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun TwoStepVerificationScreenErrorPreview() {
    PingogramPreview {
        TwoStepVerificationScreen(
            TwoStepVerificationUiState.Error(DomainError.Network),
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }
}
