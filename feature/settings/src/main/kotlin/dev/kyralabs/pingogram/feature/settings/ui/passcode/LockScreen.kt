package dev.kyralabs.pingogram.feature.settings.ui.passcode

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialog
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialogTexts
import kotlinx.coroutines.launch

/**
 * The lock screen, which the app shows instead of everything else while it is locked (`REQUIREMENTS.md` FR-S1):
 * hoists [LockViewModel]. Each time it comes to the front after a lock it shows the system's biometric prompt by
 * itself, where biometric unlock is on; unlocking buzzes ([HapticFeedbackType.Confirm]), and a failed logout for a
 * forgotten passcode shows in a snackbar.
 */
@Composable
fun AppLockRoute(
    modifier: Modifier = Modifier,
    viewModel: LockViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val prompt = rememberBiometricPrompt { viewModel.onEvent(LockEvent.BiometricSucceeded) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LockEffect.Unlocked -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                }

                is LockEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    val promptBiometrics by rememberUpdatedState(state.promptBiometrics)
    // Only once the screen is in front: the system refuses the prompt to an app in the background.
    LifecycleResumeEffect(state.promptBiometrics) {
        if (promptBiometrics) {
            viewModel.onEvent(LockEvent.BiometricPromptShown)
            prompt.show()
        }
        onPauseOrDispose {}
    }
    LockScreen(
        state = state,
        input = viewModel.input,
        onEvent = viewModel::onEvent,
        onBiometricUnlock = prompt::show,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * The lock screen on `surface`, edge to edge: a [PasscodeEntry] titled "Pingogram is locked", "Forgot passcode?" at
 * its bottom start, which asks to log out first ([DestructiveDialog]), and, where biometric unlock is on, a fingerprint
 * button ([FilledTonalIconButton]) at its bottom end. Nothing of the app shows behind it.
 */
@Composable
internal fun LockScreen(
    state: LockUiState,
    input: PasscodeInput,
    onEvent: (LockEvent) -> Unit,
    onBiometricUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        PasscodeEntry(
            texts =
                PasscodeEntryTexts(
                    title = stringResource(R.string.lock_title),
                    hint =
                        stringResource(
                            when (state.entry.type) {
                                PasscodeType.Pin -> R.string.lock_hint_pin
                                PasscodeType.Password -> R.string.lock_hint_password
                            },
                        ),
                    submit = stringResource(R.string.lock_unlock),
                ),
            state = state.entry,
            input = input,
            onSubmit = { onEvent(LockEvent.Submit) },
            modifier =
                Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            secondaryAction = { ForgotButton(state.isLoggingOut, onClick = { onEvent(LockEvent.ForgotPasscode) }) },
            extraAction = if (state.biometricUnlock) ({ BiometricButton(onBiometricUnlock) }) else null,
        )
    }
    if (state.showForgetDialog) {
        DestructiveDialog(
            icon = R.drawable.ic_logout,
            texts =
                DestructiveDialogTexts(
                    title = stringResource(R.string.lock_forgot_dialog_title),
                    text = stringResource(R.string.lock_forgot_dialog_text),
                    confirm = stringResource(R.string.lock_forgot_dialog_confirm),
                ),
            onConfirm = { onEvent(LockEvent.ConfirmForget) },
            onDismiss = { onEvent(LockEvent.DismissForget) },
        )
    }
}

/** "Forgot passcode?", or "Logging out" while it logs out. */
@Composable
private fun ForgotButton(
    isLoggingOut: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, shapes = ButtonDefaults.shapes(), enabled = !isLoggingOut) {
        Text(
            text = stringResource(if (isLoggingOut) R.string.lock_logging_out else R.string.lock_forgot),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Shows the system's biometric prompt again: the fingerprint on a tonal icon button with the Expressive shapes. */
@Composable
private fun BiometricButton(onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painter = painterResource(R.drawable.ic_fingerprint),
            contentDescription = stringResource(R.string.lock_biometric),
        )
    }
}

@Composable
private fun LockScreenPreview(
    state: LockUiState,
    digits: Int = 0,
) {
    val input = remember { PasscodeInput().apply { repeat(digits) { append('1', PasscodeType.Pin) } } }
    PingogramPreview { LockScreen(state = state, input = input, onEvent = {}, onBiometricUnlock = {}) }
}

@PingogramPreviews
@Composable
private fun LockScreenPinPreview() {
    LockScreenPreview(LockUiState(biometricUnlock = true), digits = 3)
}

@PingogramPreviews
@Composable
private fun LockScreenWrongPreview() {
    LockScreenPreview(LockUiState(entry = PasscodeEntryUiState(message = PasscodeMessage.Wrong, rejections = 1)))
}

@PingogramPreviews
@Composable
private fun LockScreenWaitPreview() {
    val entry =
        PasscodeEntryUiState(message = PasscodeMessage.Wait(PREVIEW_WAIT_SECONDS), rejections = PREVIEW_ATTEMPTS)
    LockScreenPreview(LockUiState(entry = entry))
}

@PingogramPreviews
@Composable
private fun LockScreenPasswordPreview() {
    LockScreenPreview(LockUiState(entry = PasscodeEntryUiState(type = PasscodeType.Password)))
}

@Preview(name = "Landscape", widthDp = 891, heightDp = 411)
@Composable
private fun LockScreenLandscapePreview() {
    LockScreenPreview(LockUiState(biometricUnlock = true), digits = 4)
}

private const val PREVIEW_WAIT_SECONDS = 30L
private const val PREVIEW_ATTEMPTS = 5
