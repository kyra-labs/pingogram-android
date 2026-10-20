package dev.kyralabs.pingogram.feature.settings.ui.passcode

// The rows of the Passcode lock page (PasscodeLockScreen).

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

/** No passcode: turning it on, with what the lock does and what forgetting the passcode costs. */
@Composable
internal fun PasscodeOff(onSetPasscode: () -> Unit) {
    SettingsGroup(footer = stringResource(R.string.settings_passcode_off_footer)) {
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_passcode_turn_on),
                shapes = shapes,
                onClick = onSetPasscode,
            )
        }
    }
}

/** The passcode's settings, once it was entered: the passcode, the auto-lock delay, biometrics and recent apps. */
@Composable
internal fun PasscodeOn(
    state: PasscodeLockUiState.On,
    onEvent: (PasscodeLockEvent) -> Unit,
    onSetPasscode: () -> Unit,
) {
    PasscodeGroup(onEvent, onSetPasscode)
    AutoLockGroup(state.autoLock, onEvent)
    UnlockGroup(state, onEvent)
}

/** Changing the passcode, and turning it off (destructive, in `error`). */
@Composable
private fun PasscodeGroup(
    onEvent: (PasscodeLockEvent) -> Unit,
    onSetPasscode: () -> Unit,
) {
    SettingsGroup(
        title = stringResource(R.string.settings_passcode_section_passcode),
        footer = stringResource(R.string.settings_passcode_on_footer),
    ) {
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_passcode_change),
                shapes = shapes,
                onClick = onSetPasscode,
            )
        }
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_passcode_turn_off),
                shapes = shapes,
                onClick = { onEvent(PasscodeLockEvent.TurnOff) },
                colors = destructiveItemColors(),
            )
        }
    }
}

/** The auto-lock delays as radio rows, [selected] chosen. */
@Composable
private fun AutoLockGroup(
    selected: AutoLock,
    onEvent: (PasscodeLockEvent) -> Unit,
) {
    SettingsGroup(
        title = stringResource(R.string.settings_passcode_section_auto_lock),
        footer = stringResource(R.string.settings_passcode_auto_lock_footer),
        rowsModifier = Modifier.selectableGroup(),
    ) {
        AutoLock.entries.forEach { autoLock ->
            item(key = autoLock) { shapes ->
                RadioItem(
                    label = stringResource(autoLock.labelRes()),
                    selected = autoLock == selected,
                    enabled = true,
                    shapes = shapes,
                    onClick = { if (autoLock != selected) onEvent(PasscodeLockEvent.SelectAutoLock(autoLock)) },
                )
            }
        }
    }
}

/** The switches for biometric unlock, where the device can, and for hiding the content in recent apps. */
@Composable
private fun UnlockGroup(
    state: PasscodeLockUiState.On,
    onEvent: (PasscodeLockEvent) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_passcode_section_unlock)) {
        if (state.biometricAvailable) {
            item { shapes ->
                SwitchItem(
                    title = stringResource(R.string.settings_passcode_biometric),
                    summary = stringResource(R.string.settings_passcode_biometric_summary),
                    checked = state.biometricUnlock,
                    shapes = shapes,
                    onCheckedChange = { onEvent(PasscodeLockEvent.SetBiometricUnlock(it)) },
                )
            }
        }
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_passcode_hide_content),
                summary =
                    stringResource(
                        // Before Android 13 only FLAG_SECURE hides the content, and it blocks screenshots too.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            R.string.settings_passcode_hide_content_summary
                        } else {
                            R.string.settings_passcode_hide_content_summary_secure
                        },
                    ),
                checked = state.hideContent,
                shapes = shapes,
                onCheckedChange = { onEvent(PasscodeLockEvent.SetHideContent(it)) },
            )
        }
    }
}

private fun AutoLock.labelRes(): Int =
    when (this) {
        AutoLock.Immediately -> R.string.settings_passcode_auto_lock_immediately
        AutoLock.OneMinute -> R.string.settings_passcode_auto_lock_one_minute
        AutoLock.FiveMinutes -> R.string.settings_passcode_auto_lock_five_minutes
        AutoLock.OneHour -> R.string.settings_passcode_auto_lock_one_hour
    }
