package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.durationText
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthCodeField
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPrimaryButton
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthStepLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
import dev.kyralabs.pingogram.feature.auth.ui.components.fieldError
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthScreenPreview
import dev.kyralabs.pingogram.core.ui.R as UiR

/** FR-A7: the email address Telegram requires for this account. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmailAddressScreen(
    state: AuthStepUiState.EmailAddress,
    canSubmit: Boolean,
    pendingAction: AuthAction?,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthStepLayout(
        title =
            stringResource(
                if (state.isChangingAddress) R.string.auth_email_change_title else R.string.auth_email_title,
            ),
        badgeShape = MaterialShapes.Puffy,
        badgeIcon = UiR.drawable.ic_alternate_email,
        description = stringResource(R.string.auth_email_description),
        isError = state.error != null,
        modifier = modifier,
        primaryAction = {
            AuthPrimaryButton(
                text = stringResource(R.string.auth_next),
                onClick = { onEvent(AuthEvent.SubmitEmailAddress) },
                enabled = canSubmit && state.emailAddress.isNotEmpty(),
                inProgress = pendingAction == AuthAction.SubmitEmailAddress,
            )
        },
    ) {
        val error = state.error?.let { errorText(it) }
        TextField(
            value = state.emailAddress,
            onValueChange = { onEvent(AuthEvent.EmailAddressChanged(it)) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fieldError(error)
                    .semantics { contentType = ContentType.EmailAddress },
            label = { Text(stringResource(R.string.auth_email_label)) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { onEvent(AuthEvent.SubmitEmailAddress) }),
        )
    }
}

/** FR-A7: the code sent to the email address; resend, another address, or resetting the address. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmailCodeScreen(
    state: AuthStepUiState.EmailCode,
    canSubmit: Boolean,
    pendingAction: AuthAction?,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthStepLayout(
        title = stringResource(R.string.auth_email_code_title),
        badgeShape = MaterialShapes.Flower,
        badgeIcon = UiR.drawable.ic_alternate_email,
        description = stringResource(R.string.auth_email_code_description, state.emailAddressPattern),
        isError = state.error != null,
        modifier = modifier,
        primaryAction = {
            AuthPrimaryButton(
                text = stringResource(R.string.auth_next),
                onClick = { onEvent(AuthEvent.SubmitEmailCode) },
                enabled = canSubmit && state.code.isNotEmpty(),
                inProgress = pendingAction == AuthAction.SubmitEmailCode,
            )
        },
        secondaryAction = {
            AuthTextButton(
                text = stringResource(R.string.auth_email_change),
                onClick = { onEvent(AuthEvent.ChangeEmailAddress) },
            )
        },
    ) {
        AuthCodeField(
            value = state.code,
            onValueChange = { onEvent(AuthEvent.EmailCodeChanged(it)) },
            label = stringResource(R.string.auth_code_label),
            error = state.error?.let { errorText(it) },
            onDone = { onEvent(AuthEvent.SubmitEmailCode) },
        )
        if (state.canResendCode) {
            AuthTextButton(
                text = stringResource(R.string.auth_email_resend),
                onClick = { onEvent(AuthEvent.ResendEmailCode) },
                enabled = canSubmit,
                inProgress = pendingAction == AuthAction.ResendEmailCode,
            )
        }
        state.reset?.let {
            EmailReset(
                reset = it,
                canSubmit = canSubmit,
                pendingAction = pendingAction,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun EmailReset(
    reset: EmailResetUiState,
    canSubmit: Boolean,
    pendingAction: AuthAction?,
    onEvent: (AuthEvent) -> Unit,
) {
    val text =
        when (reset) {
            is EmailResetUiState.Available -> {
                if (reset.waitSeconds == 0L) {
                    stringResource(R.string.auth_email_reset_available)
                } else {
                    stringResource(R.string.auth_email_reset_wait, durationText(reset.waitSeconds))
                }
            }

            is EmailResetUiState.Pending -> {
                stringResource(R.string.auth_email_reset_pending, durationText(reset.resetInSeconds))
            }
        }
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    AuthTextButton(
        text =
            stringResource(
                if (reset is EmailResetUiState.Pending) R.string.auth_email_reset_check else R.string.auth_email_reset,
            ),
        onClick = { onEvent(AuthEvent.ResetEmailAddress) },
        enabled = canSubmit,
        inProgress = pendingAction == AuthAction.ResetEmailAddress,
    )
}

@PingogramPreviews
@Composable
private fun EmailAddressScreenPreview() {
    AuthScreenPreview(AuthPreviewData.emailAddress)
}

@PingogramPreviews
@Composable
private fun EmailCodeScreenPreview() {
    AuthScreenPreview(AuthPreviewData.emailCode)
}

@PingogramPreviews
@Composable
private fun EmailCodeScreenResetPendingPreview() {
    AuthScreenPreview(AuthPreviewData.emailCodeResetPending)
}
