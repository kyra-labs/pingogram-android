package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthCodeField
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthNotice
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPasswordField
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPrimaryButton
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthStepLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthScreenPreview

/** FR-A4: the 2-step verification password, its hint, and "forgot password". */
@Composable
fun PasswordScreen(
    state: AuthStepUiState.Password,
    canSubmit: Boolean,
    pendingAction: AuthAction?,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthStepLayout(
        title = stringResource(R.string.auth_password_title),
        description = stringResource(R.string.auth_password_description),
        modifier = modifier,
        actions = {
            AuthPrimaryButton(
                text = stringResource(R.string.auth_next),
                onClick = { onEvent(AuthEvent.SubmitPassword) },
                enabled = canSubmit && state.password.isNotEmpty(),
                inProgress = pendingAction == AuthAction.SubmitPassword,
            )
            AuthTextButton(
                text = stringResource(R.string.auth_forgot_password),
                onClick = { onEvent(AuthEvent.ForgotPassword) },
                enabled = canSubmit,
                inProgress = pendingAction == AuthAction.RequestPasswordRecovery,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        },
    ) {
        AuthPasswordField(
            value = state.password,
            onValueChange = { onEvent(AuthEvent.PasswordChanged(it)) },
            label = stringResource(R.string.auth_password_label),
            error = state.error?.let { errorText(it) },
            supportingText =
                state.hint.takeIf { it.isNotBlank() }?.let {
                    stringResource(
                        R.string.auth_password_hint,
                        it,
                    )
                },
            onDone = { onEvent(AuthEvent.SubmitPassword) },
        )
        if (state.showNoRecoveryEmailInfo) {
            AuthNotice(text = stringResource(R.string.auth_password_no_recovery_email))
        }
    }
}

/** FR-A4, forgot password: the recovery code sent to the recovery email address. */
@Composable
fun PasswordRecoveryCodeScreen(
    state: AuthStepUiState.PasswordRecoveryCode,
    canSubmit: Boolean,
    pendingAction: AuthAction?,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthStepLayout(
        title = stringResource(R.string.auth_recovery_title),
        description =
            if (state.emailAddressPattern.isBlank()) {
                stringResource(R.string.auth_recovery_description_unknown)
            } else {
                stringResource(R.string.auth_recovery_description, state.emailAddressPattern)
            },
        modifier = modifier,
        actions = {
            AuthPrimaryButton(
                text = stringResource(R.string.auth_next),
                onClick = { onEvent(AuthEvent.SubmitRecoveryCode) },
                enabled = canSubmit && state.code.isNotEmpty(),
                inProgress = pendingAction == AuthAction.SubmitRecoveryCode,
            )
        },
    ) {
        AuthCodeField(
            value = state.code,
            onValueChange = { onEvent(AuthEvent.RecoveryCodeChanged(it)) },
            label = stringResource(R.string.auth_recovery_code_label),
            error = state.error?.let { errorText(it) },
            onDone = { onEvent(AuthEvent.SubmitRecoveryCode) },
        )
    }
}

/** FR-A4, forgot password: the new password, twice, and an optional hint; submitting logs in. */
@Composable
fun NewPasswordScreen(
    state: AuthStepUiState.NewPassword,
    canSubmit: Boolean,
    pendingAction: AuthAction?,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthStepLayout(
        title = stringResource(R.string.auth_new_password_title),
        description = stringResource(R.string.auth_new_password_description),
        modifier = modifier,
        actions = {
            AuthPrimaryButton(
                text = stringResource(R.string.auth_new_password_submit),
                onClick = { onEvent(AuthEvent.SubmitNewPassword) },
                enabled = canSubmit && state.isComplete,
                inProgress = pendingAction == AuthAction.SubmitNewPassword,
            )
        },
    ) {
        AuthPasswordField(
            value = state.password,
            onValueChange = { onEvent(AuthEvent.NewPasswordChanged(it)) },
            label = stringResource(R.string.auth_new_password_label),
            error = state.error,
            isNewPassword = true,
            imeAction = ImeAction.Next,
        )
        AuthPasswordField(
            value = state.confirmation,
            onValueChange = { onEvent(AuthEvent.NewPasswordConfirmationChanged(it)) },
            label = stringResource(R.string.auth_new_password_confirmation_label),
            error = if (state.isMismatch) stringResource(R.string.auth_new_password_mismatch) else null,
            isNewPassword = true,
            imeAction = ImeAction.Next,
        )
        TextField(
            value = state.hint,
            onValueChange = { onEvent(AuthEvent.NewPasswordHintChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.auth_new_password_hint_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEvent(AuthEvent.SubmitNewPassword) }),
        )
    }
}

@PingogramPreviews
@Composable
private fun PasswordScreenPreview() {
    AuthScreenPreview(AuthPreviewData.password)
}

@PingogramPreviews
@Composable
private fun PasswordScreenErrorPreview() {
    AuthScreenPreview(AuthPreviewData.passwordError)
}

@PingogramPreviews
@Composable
private fun PasswordScreenNoRecoveryEmailPreview() {
    AuthScreenPreview(AuthPreviewData.passwordNoRecoveryEmail)
}

@PingogramPreviews
@Composable
private fun PasswordRecoveryCodeScreenPreview() {
    AuthScreenPreview(AuthPreviewData.passwordRecoveryCode)
}

@PingogramPreviews
@Composable
private fun NewPasswordScreenPreview() {
    AuthScreenPreview(AuthPreviewData.newPassword)
}
