package dev.kyralabs.pingogram.feature.settings.ui.passcode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsBody
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageFrame

/**
 * The Set passcode page: hoists [SetPasscodeViewModel] and leaves through [onSaved] once the passcode is set, with a
 * [HapticFeedbackType.Confirm]. Back, from the second entry, returns to the first; [onBack] leaves from the first.
 */
@Composable
internal fun SetPasscodeRoute(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetPasscodeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val currentOnSaved by rememberUpdatedState(onSaved)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SetPasscodeEffect.Saved -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    currentOnSaved()
                }
            }
        }
    }
    SetPasscodeScreen(
        state = state,
        input = viewModel.input,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Set passcode (`REQUIREMENTS.md` FR-S1): a [PasscodeEntry] that asks for a PIN, with "Use a password" at its bottom
 * start to switch (and back), then for the same passcode again, which saves it. The keyboard pushes the page up for a
 * password.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SetPasscodeScreen(
    state: SetPasscodeUiState,
    input: PasscodeInput,
    onEvent: (SetPasscodeEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val confirming = state.step == SetPasscodeStep.Confirm
    BackHandler(enabled = confirming) { onEvent(SetPasscodeEvent.Back) }
    val type = state.entry.type
    SettingsPageFrame(
        title = stringResource(R.string.settings_passcode_set_title),
        onBack = { if (confirming) onEvent(SetPasscodeEvent.Back) else onBack() },
        modifier = modifier,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) { innerPadding ->
        SettingsBody(innerPadding) { contentPadding ->
            PasscodeEntry(
                texts = setPasscodeTexts(type, confirming),
                state = state.entry,
                input = input,
                onSubmit = { onEvent(SetPasscodeEvent.Submit) },
                modifier = Modifier.padding(contentPadding),
                secondaryAction =
                    if (confirming) {
                        null
                    } else {
                        {
                            TextButton(
                                onClick = { onEvent(SetPasscodeEvent.SwitchType) },
                                shapes = ButtonDefaults.shapes(),
                                enabled = !state.entry.isChecking,
                            ) {
                                Text(
                                    text =
                                        stringResource(
                                            if (type == PasscodeType.Pin) {
                                                R.string.settings_passcode_use_password
                                            } else {
                                                R.string.settings_passcode_use_pin
                                            },
                                        ),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    },
            )
        }
    }
}

@Composable
private fun setPasscodeTexts(
    type: PasscodeType,
    confirming: Boolean,
) = PasscodeEntryTexts(
    title = stringResource(setPasscodeTitle(type, confirming)),
    hint =
        stringResource(
            when {
                confirming -> R.string.settings_passcode_confirm_hint
                type == PasscodeType.Pin -> R.string.settings_passcode_create_pin_hint
                else -> R.string.settings_passcode_create_password_hint
            },
        ),
    submit = stringResource(if (confirming) R.string.settings_passcode_save else R.string.settings_passcode_next),
)

private fun setPasscodeTitle(
    type: PasscodeType,
    confirming: Boolean,
): Int =
    when (type) {
        PasscodeType.Pin -> {
            if (confirming) R.string.settings_passcode_confirm_pin else R.string.settings_passcode_create_pin
        }

        PasscodeType.Password -> {
            if (confirming) R.string.settings_passcode_confirm_password else R.string.settings_passcode_create_password
        }
    }

@Composable
private fun SetPasscodeScreenPreview(
    state: SetPasscodeUiState,
    digits: Int = 0,
) {
    val input = remember { PasscodeInput().apply { repeat(digits) { append('2', PasscodeType.Pin) } } }
    PingogramPreview { SetPasscodeScreen(state = state, input = input, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun SetPasscodeScreenChoosePreview() {
    SetPasscodeScreenPreview(SetPasscodeUiState(), digits = 2)
}

@PingogramPreviews
@Composable
private fun SetPasscodeScreenConfirmPreview() {
    SetPasscodeScreenPreview(SetPasscodeUiState(step = SetPasscodeStep.Confirm), digits = 4)
}

@PingogramPreviews
@Composable
private fun SetPasscodeScreenMismatchPreview() {
    SetPasscodeScreenPreview(
        SetPasscodeUiState(entry = PasscodeEntryUiState(message = PasscodeMessage.Mismatch, rejections = 1)),
    )
}

@PingogramPreviews
@Composable
private fun SetPasscodeScreenPasswordPreview() {
    SetPasscodeScreenPreview(SetPasscodeUiState(entry = PasscodeEntryUiState(type = PasscodeType.Password)))
}
