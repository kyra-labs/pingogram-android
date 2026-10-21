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
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
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

// The groups of rows of the 2-step verification page (TwoStepVerificationScreen) and its confirmations.

/** With a password: changing it, and turning it off ([onTurnOff], which asks first). */
@Composable
internal fun PasswordGroup(
    enabled: Boolean,
    onOpen: (TwoStepVerificationFlow) -> Unit,
    onTurnOff: () -> Unit,
) {
    SettingsGroup(
        title = stringResource(R.string.settings_two_step_section_password),
        footer = stringResource(R.string.settings_two_step_about),
    ) {
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_two_step_change_password),
                shapes = shapes,
                enabled = enabled,
                onClick = { onOpen(TwoStepVerificationFlow.ChangePassword) },
            )
        }
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_two_step_turn_off),
                shapes = shapes,
                enabled = enabled,
                colors = destructiveItemColors(),
                onClick = onTurnOff,
            )
        }
    }
}

/** Without a password: setting one, and what it does. */
@Composable
internal fun SetPasswordGroup(
    enabled: Boolean,
    onOpen: (TwoStepVerificationFlow) -> Unit,
) {
    SettingsGroup(footer = stringResource(R.string.settings_two_step_about)) {
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_two_step_set_password),
                shapes = shapes,
                enabled = enabled,
                onClick = { onOpen(TwoStepVerificationFlow.SetPassword) },
            )
        }
    }
}

/** Changing or adding the recovery email, with whether one is set. */
@Composable
internal fun RecoveryEmailGroup(
    hasRecoveryEmail: Boolean,
    enabled: Boolean,
    onOpen: (TwoStepVerificationFlow) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_two_step_section_recovery_email)) {
        item { shapes ->
            SettingsRow(
                title =
                    stringResource(
                        if (hasRecoveryEmail) {
                            R.string.settings_two_step_change_recovery_email
                        } else {
                            R.string.settings_two_step_add_recovery_email
                        },
                    ),
                shapes = shapes,
                enabled = enabled,
                supportingContent = {
                    Text(
                        stringResource(
                            if (hasRecoveryEmail) {
                                R.string.settings_two_step_recovery_email_set
                            } else {
                                R.string.settings_two_step_recovery_email_not_set
                            },
                        ),
                    )
                },
                onClick = { onOpen(TwoStepVerificationFlow.ChangeRecoveryEmail) },
            )
        }
    }
}

/** A recovery email waiting for its code: entering it, or giving the address up. */
@Composable
internal fun PendingEmailGroup(
    pattern: String,
    hasPassword: Boolean,
    enabled: Boolean,
    onEvent: (TwoStepVerificationEvent) -> Unit,
    onOpen: (TwoStepVerificationFlow) -> Unit,
) {
    SettingsGroup(
        title = stringResource(R.string.settings_two_step_section_recovery_email),
        footer = if (hasPassword) null else stringResource(R.string.settings_two_step_about_pending),
    ) {
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_two_step_enter_email_code),
                shapes = shapes,
                enabled = enabled,
                supportingContent = {
                    Text(
                        stringResource(R.string.settings_two_step_email_code_sent_to, pattern.bidiIsolated())
                            .bidiIsolated(),
                    )
                },
                onClick = { onOpen(TwoStepVerificationFlow.ConfirmRecoveryEmail) },
            )
        }
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_two_step_cancel_email),
                shapes = shapes,
                enabled = enabled,
                onClick = { onEvent(TwoStepVerificationEvent.CancelRecoveryEmail) },
            )
        }
    }
}

/** A reset of the password that waits: when, completing it once that time has passed, and cancelling it. */
@Composable
internal fun PendingResetGroup(
    reset: PendingPasswordReset,
    enabled: Boolean,
    onEvent: (TwoStepVerificationEvent) -> Unit,
    onCompleteReset: () -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_two_step_section_reset)) {
        item { shapes -> ResetStateRow(reset, shapes) }
        if (reset.canCompleteNow) {
            item { shapes ->
                SettingsRow(
                    title = stringResource(R.string.settings_two_step_reset_now),
                    shapes = shapes,
                    enabled = enabled,
                    colors = destructiveItemColors(),
                    onClick = onCompleteReset,
                )
            }
        }
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_two_step_cancel_reset),
                shapes = shapes,
                enabled = enabled,
                onClick = { onEvent(TwoStepVerificationEvent.CancelReset) },
            )
        }
    }
}

@Composable
private fun ResetStateRow(
    reset: PendingPasswordReset,
    shapes: ListItemShapes,
) {
    SettingsRow(
        title = stringResource(R.string.settings_two_step_reset_requested),
        shapes = shapes,
        supportingContent = {
            Text(
                if (reset.canCompleteNow) {
                    stringResource(R.string.settings_two_step_reset_ready)
                } else {
                    stringResource(R.string.settings_two_step_reset_after, reset.date)
                },
            )
        },
    )
}

/** Turning 2-step verification off: a destructive confirmation before the flow asks for the password. */
@Composable
internal fun TurnOffDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DestructiveDialog(
        icon = R.drawable.ic_password,
        texts =
            DestructiveDialogTexts(
                title = stringResource(R.string.settings_two_step_turn_off_title),
                text = stringResource(R.string.settings_two_step_turn_off_text),
                confirm = stringResource(R.string.settings_two_step_turn_off),
            ),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Completing a reset whose waiting period is over: a destructive confirmation. */
@Composable
internal fun CompleteResetDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DestructiveDialog(
        icon = R.drawable.ic_password,
        texts =
            DestructiveDialogTexts(
                title = stringResource(R.string.settings_two_step_reset_title),
                text = stringResource(R.string.settings_two_step_reset_now_text),
                confirm = stringResource(R.string.settings_two_step_reset_confirm),
            ),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
