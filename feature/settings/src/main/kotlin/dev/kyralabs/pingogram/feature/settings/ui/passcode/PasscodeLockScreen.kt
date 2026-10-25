package dev.kyralabs.pingogram.feature.settings.ui.passcode

import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.AutoLock
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialog
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialogTexts
import dev.kyralabs.pingogram.feature.settings.ui.RadioItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsBody
import dev.kyralabs.pingogram.feature.settings.ui.SettingsContent
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageFrame
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem
import dev.kyralabs.pingogram.feature.settings.ui.destructiveItemColors
import kotlinx.coroutines.launch

/**
 * The Passcode lock page: hoists [PasscodeLockViewModel]; checks the biometrics again whenever the page shows again.
 * [onSetPasscode] opens the page that sets a new passcode; [onBack] leaves.
 */
@Composable
internal fun PasscodeLockRoute(
    onSetPasscode: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PasscodeLockViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(PasscodeLockEvent.Refresh)
        onStopOrDispose {}
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PasscodeLockEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    PasscodeLockScreen(
        state = state,
        input = viewModel.input,
        onEvent = viewModel::onEvent,
        onSetPasscode = onSetPasscode,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Passcode lock (`REQUIREMENTS.md` FR-S1), a settings page without row icons:
 * - off: "Turn passcode on", with what the lock does and what forgetting the passcode costs;
 * - set, before the passcode was entered here: a [PasscodeEntry];
 * - set: changing and turning off the passcode (destructive, asking first), the auto-lock delay as radio rows, and
 *   the switches for biometric unlock (where the device has strong biometrics) and for hiding the content in recent
 *   apps, which before Android 13 also blocks screenshots.
 */
@Composable
internal fun PasscodeLockScreen(
    state: PasscodeLockUiState,
    input: PasscodeInput,
    onEvent: (PasscodeLockEvent) -> Unit,
    onSetPasscode: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageFrame(
        title = stringResource(R.string.settings_passcode_lock),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        when (state) {
            PasscodeLockUiState.Loading -> {
                SettingsContent(innerPadding) {
                    SettingsLoading(stringResource(R.string.settings_passcode_lock_loading))
                }
            }

            PasscodeLockUiState.Off -> {
                SettingsContent(innerPadding) { PasscodeOff(onSetPasscode) }
            }

            is PasscodeLockUiState.Verify -> {
                SettingsBody(innerPadding) { contentPadding ->
                    PasscodeEntry(
                        texts =
                            PasscodeEntryTexts(
                                title = stringResource(R.string.settings_passcode_verify_title),
                                hint = stringResource(R.string.settings_passcode_verify_hint),
                                submit = stringResource(R.string.settings_passcode_next),
                            ),
                        state = state.entry,
                        input = input,
                        onSubmit = { onEvent(PasscodeLockEvent.Submit) },
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }

            is PasscodeLockUiState.On -> {
                SettingsContent(innerPadding) { PasscodeOn(state, onEvent, onSetPasscode) }
            }
        }
    }
    if (state is PasscodeLockUiState.On && state.showTurnOffDialog) {
        DestructiveDialog(
            icon = null,
            texts =
                DestructiveDialogTexts(
                    title = stringResource(R.string.settings_passcode_turn_off_dialog_title),
                    text = stringResource(R.string.settings_passcode_turn_off_dialog_text),
                    confirm = stringResource(R.string.settings_passcode_turn_off_dialog_confirm),
                ),
            onConfirm = { onEvent(PasscodeLockEvent.ConfirmTurnOff) },
            onDismiss = { onEvent(PasscodeLockEvent.DismissTurnOff) },
        )
    }
}

/** The page with the passcode set and entered, as previews and screenshots show it. */
internal val PasscodeLockOnPreview =
    PasscodeLockUiState.On(
        autoLock = AutoLock.OneMinute,
        biometricAvailable = true,
        biometricUnlock = true,
        hideContent = true,
        screenLockAvailable = true,
    )

@Composable
private fun PasscodeLockScreenPreview(state: PasscodeLockUiState) {
    val input = remember { PasscodeInput() }
    PingogramPreview {
        PasscodeLockScreen(state = state, input = input, onEvent = {}, onSetPasscode = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun PasscodeLockScreenOnPreview() {
    PasscodeLockScreenPreview(PasscodeLockOnPreview)
}

@PingogramPreviews
@Composable
private fun PasscodeLockScreenOffPreview() {
    PasscodeLockScreenPreview(PasscodeLockUiState.Off)
}

@PingogramPreviews
@Composable
private fun PasscodeLockScreenVerifyPreview() {
    PasscodeLockScreenPreview(PasscodeLockUiState.Verify(PasscodeEntryUiState(type = PasscodeType.Pin)))
}

@PingogramPreviews
@Composable
private fun PasscodeLockScreenLoadingPreview() {
    PasscodeLockScreenPreview(PasscodeLockUiState.Loading)
}

@Preview(name = "Turn off", widthDp = 411, heightDp = 891)
@Composable
private fun PasscodeLockScreenTurnOffPreview() {
    PasscodeLockScreenPreview(PasscodeLockOnPreview.copy(showTurnOffDialog = true))
}
