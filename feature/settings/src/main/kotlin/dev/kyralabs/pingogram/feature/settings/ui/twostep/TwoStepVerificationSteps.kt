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

// The steps of 2-step verification's flows (TwoStepVerificationFlowScreen), each a column of text, fields and actions.

@Composable
internal fun ColumnScope.CurrentPasswordStep(
    state: TwoStepVerificationFlowUiState,
    onEvent: (Event) -> Unit,
) {
    val password = remember { TextFieldState() }
    val focus = remember { FocusRequester() }
    val submit = { onEvent(Event.SubmitCurrentPassword(password.text.toString())) }
    FirstField(focus, password) { onEvent(Event.InputChanged) }
    StepText(stringResource(R.string.settings_two_step_current_password_text))
    if (state.passwordHint.isNotEmpty()) {
        StepText(
            stringResource(R.string.settings_two_step_hint_is, state.passwordHint),
        )
    }
    TwoStepPasswordField(
        state = password,
        spec = state.fieldSpec(stringResource(R.string.settings_two_step_label_password), ImeAction.Done, submit),
        isNewPassword = false,
        modifier = Modifier.focusRequester(focus),
    )
    StepActions(
        state = state,
        primary =
            if (state.flow == TwoStepVerificationFlow.RemovePassword) {
                stringResource(R.string.settings_two_step_turn_off)
            } else {
                stringResource(R.string.settings_two_step_next)
            },
        onPrimary = submit,
        secondary = stringResource(R.string.settings_two_step_forgot_password),
        onSecondary = { onEvent(Event.ForgotPassword) },
    )
}

@Composable
internal fun ColumnScope.NewPasswordStep(
    state: TwoStepVerificationFlowUiState,
    onEvent: (Event) -> Unit,
) {
    val password = remember { TextFieldState() }
    val repeated = remember { TextFieldState() }
    val focus = remember { FocusRequester() }
    val repeatFocus = remember { FocusRequester() }
    val submit = { onEvent(Event.SubmitNewPassword(password.text.toString(), repeated.text.toString())) }
    FirstField(focus, password) { onEvent(Event.InputChanged) }
    WatchChanges(repeated) { onEvent(Event.InputChanged) }
    StepText(stringResource(R.string.settings_two_step_new_password_text))
    TwoStepPasswordField(
        state = password,
        // The error says the repetition differs: it shows at the repetition alone.
        spec =
            state.fieldSpec(
                label = stringResource(R.string.settings_two_step_label_new_password),
                imeAction = ImeAction.Next,
                onAction = { repeatFocus.requestFocus() },
                showsError = false,
            ),
        isNewPassword = true,
        modifier = Modifier.focusRequester(focus),
    )
    TwoStepPasswordField(
        state = repeated,
        spec =
            state.fieldSpec(
                stringResource(R.string.settings_two_step_label_repeat_password),
                ImeAction.Done,
                submit,
            ),
        isNewPassword = true,
        modifier = Modifier.focusRequester(repeatFocus),
    )
    StepActions(state = state, primary = stringResource(R.string.settings_two_step_next), onPrimary = submit)
}

@Composable
internal fun ColumnScope.HintStep(
    state: TwoStepVerificationFlowUiState,
    onEvent: (Event) -> Unit,
) {
    val hint = remember { TextFieldState() }
    val focus = remember { FocusRequester() }
    val submit = { onEvent(Event.SubmitHint(hint.text.toString())) }
    val finishes = state.recovering || state.flow == TwoStepVerificationFlow.ChangePassword
    FirstField(focus, hint) { onEvent(Event.InputChanged) }
    StepText(stringResource(R.string.settings_two_step_hint_text))
    TwoStepTextField(
        state = hint,
        spec = state.fieldSpec(stringResource(R.string.settings_two_step_label_hint), ImeAction.Done, submit),
        keyboardType = KeyboardType.Text,
        modifier = Modifier.focusRequester(focus),
    )
    StepActions(
        state = state,
        primary = stringResource(if (finishes) R.string.settings_two_step_save else R.string.settings_two_step_next),
        onPrimary = submit,
    )
}

@Composable
internal fun ColumnScope.RecoveryEmailStep(
    state: TwoStepVerificationFlowUiState,
    onEvent: (Event) -> Unit,
) {
    val email = remember { TextFieldState() }
    val focus = remember { FocusRequester() }
    val submit = { onEvent(Event.SubmitRecoveryEmail(email.text.toString())) }
    FirstField(focus, email) { onEvent(Event.InputChanged) }
    StepText(stringResource(R.string.settings_two_step_recovery_email_text))
    TwoStepTextField(
        state = email,
        spec = state.fieldSpec(stringResource(R.string.settings_two_step_label_email), ImeAction.Done, submit),
        keyboardType = KeyboardType.Email,
        modifier = Modifier.focusRequester(focus),
        autofill = ContentType.EmailAddress,
    )
    val canSkip = state.flow == TwoStepVerificationFlow.SetPassword
    StepActions(
        state = state,
        primary = stringResource(R.string.settings_two_step_next),
        onPrimary = submit,
        secondary = if (canSkip) stringResource(R.string.settings_two_step_skip) else null,
        onSecondary = { onEvent(Event.SkipRecoveryEmail) },
    )
}

/** The code sent to the recovery email: to confirm it, or, with [recovery], to reset a forgotten password. */
@Composable
internal fun ColumnScope.CodeStep(
    state: TwoStepVerificationFlowUiState,
    recovery: Boolean,
    onEvent: (Event) -> Unit,
) {
    val code = remember { TextFieldState() }
    val focus = remember { FocusRequester() }
    val submit = {
        val text = code.text.toString()
        onEvent(if (recovery) Event.SubmitRecoveryCode(text) else Event.SubmitEmailCode(text))
    }
    FirstField(focus, code) { onEvent(Event.InputChanged) }
    val pattern = state.emailPattern
    StepText(
        when {
            recovery -> stringResource(R.string.settings_two_step_recovery_code_text, pattern.orEmpty())
            pattern != null -> stringResource(R.string.settings_two_step_email_code_text, pattern)
            else -> stringResource(R.string.settings_two_step_email_code_text_unknown)
        },
    )
    TwoStepTextField(
        state = code,
        spec = state.fieldSpec(stringResource(R.string.settings_two_step_label_code), ImeAction.Done, submit),
        keyboardType = KeyboardType.Number,
        modifier = Modifier.focusRequester(focus),
        autofill = EmailOtpCode,
    )
    StepActions(
        state = state,
        primary = stringResource(R.string.settings_two_step_next),
        onPrimary = submit,
        secondary =
            stringResource(
                if (recovery) R.string.settings_two_step_no_access else R.string.settings_two_step_resend_code,
            ),
        onSecondary = { onEvent(if (recovery) Event.NoAccessToEmail else Event.ResendEmailCode) },
    )
}

/** Resetting the password without a recovery email, behind a destructive confirmation. */
@Composable
internal fun ColumnScope.ResetStep(
    state: TwoStepVerificationFlowUiState,
    onEvent: (Event) -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    StepText(stringResource(R.string.settings_two_step_reset_step_text))
    StepActions(
        state = state,
        primary = stringResource(R.string.settings_two_step_reset_password),
        onPrimary = { confirm = true },
    )
    if (confirm) {
        DestructiveDialog(
            icon = R.drawable.ic_password,
            texts =
                DestructiveDialogTexts(
                    title = stringResource(R.string.settings_two_step_reset_title),
                    text = stringResource(R.string.settings_two_step_reset_text),
                    confirm = stringResource(R.string.settings_two_step_reset_confirm),
                ),
            onConfirm = {
                confirm = false
                onEvent(Event.ConfirmReset)
            },
            onDismiss = { confirm = false },
        )
    }
}

/** What a step asks for: `bodyMedium` in `onSurfaceVariant`, inset like the settings notes. */
@Composable
private fun StepText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * The end of a step: the `FLOOD_WAIT` countdown and the progress of a request, then [secondary], if any, as a
 * [TextButton] at the start and [primary] as a default [Button] at the end, both with the press morph; they wrap onto
 * two lines when they do not fit side by side.
 */
@Composable
private fun StepActions(
    state: TwoStepVerificationFlowUiState,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    state.floodWaitSeconds?.let { FloodWaitNote(seconds = it, totalSeconds = state.floodWaitTotalSeconds) }
    if (state.isWorking) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (secondary != null) Arrangement.SpaceBetween else Arrangement.End,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        if (secondary != null) {
            TextButton(onClick = onSecondary, enabled = !state.isWorking, shapes = ButtonDefaults.shapes()) {
                Text(secondary, style = MaterialTheme.typography.labelLarge)
            }
        }
        Button(onClick = onPrimary, enabled = state.canSubmit, shapes = ButtonDefaults.shapes()) {
            Text(primary, style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/** The step's fields: enabled unless a request is on its way, with the step's error if [showsError]. */
@Composable
private fun TwoStepVerificationFlowUiState.fieldSpec(
    label: String,
    imeAction: ImeAction,
    onAction: () -> Unit,
    showsError: Boolean = true,
) = TwoStepFieldSpec(
    label = label,
    error = if (showsError) fieldErrorText(fieldError) else null,
    enabled = !isWorking,
    imeAction = imeAction,
    onAction = onAction,
)

/** The autofill hint of a code sent by email (Android's `AUTOFILL_HINT_EMAIL_OTP`). */
private val EmailOtpCode: ContentType = ContentType("emailOTPCode")
