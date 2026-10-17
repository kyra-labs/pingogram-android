package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.durationText
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPrimaryButton
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthStepLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthScreenPreview

/** FR-A7: the email address Telegram requires for this account. */
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
        description = stringResource(R.string.auth_email_description),
        modifier = modifier,
        actions = {
            AuthPrimaryButton(
                text = stringResource(R.string.auth_next),
                onClick = { onEvent(AuthEvent.SubmitEmailAddress) },
                enabled = canSubmit && state.emailAddress.isNotEmpty(),
                inProgress = pendingAction == AuthAction.SubmitEmailAddress,
            )
        },
    ) {
        TextField(
            value = state.emailAddress,
            onValueChange = { onEvent(AuthEvent.EmailAddressChanged(it)) },
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
            label = { Text(stringResource(R.string.auth_email_label)) },
            isError = state.error != null,
            supportingText = state.error?.let { error -> { Text(errorText(error)) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEvent(AuthEvent.SubmitEmailAddress) }),
        )
    }
}

/** FR-A7: the code sent to the email address; resend, another address, or resetting the address. */
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
        description = stringResource(R.string.auth_email_code_description, state.emailAddressPattern),
        modifier = modifier,
        actions = {
            AuthPrimaryButton(
                text = stringResource(R.string.auth_next),
                onClick = { onEvent(AuthEvent.SubmitEmailCode) },
                enabled = canSubmit && state.code.isNotEmpty(),
                inProgress = pendingAction == AuthAction.SubmitEmailCode,
            )
            AuthTextButton(
                text = stringResource(R.string.auth_email_change),
                onClick = { onEvent(AuthEvent.ChangeEmailAddress) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        },
    ) {
        TextField(
            value = state.code,
            onValueChange = { onEvent(AuthEvent.EmailCodeChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.auth_code_label)) },
            isError = state.error != null,
            supportingText = state.error?.let { error -> { Text(errorText(error)) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEvent(AuthEvent.SubmitEmailCode) }),
        )
        AuthTextButton(
            text = stringResource(R.string.auth_email_resend),
            onClick = { onEvent(AuthEvent.ResendEmailCode) },
            enabled = canSubmit,
            inProgress = pendingAction == AuthAction.ResendEmailCode,
        )
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

private const val PREVIEW_CODE_LENGTH = 6
private const val PREVIEW_RESET_WAIT_SECONDS = 604_800L
private const val PREVIEW_RESET_PENDING_SECONDS = 3_600L

@PingogramPreviews
@Composable
private fun EmailAddressScreenPreview() {
    AuthScreenPreview(
        AuthPreviewData.state(AuthStepUiState.EmailAddress("ada@example", DomainError.EmailInvalid, false)),
    )
}

@PingogramPreviews
@Composable
private fun EmailCodeScreenPreview() {
    AuthScreenPreview(
        AuthPreviewData.state(
            AuthStepUiState.EmailCode(
                emailAddressPattern = "a***@example.org",
                code = "12",
                codeLength = PREVIEW_CODE_LENGTH,
                error = null,
                reset = EmailResetUiState.Available(waitSeconds = PREVIEW_RESET_WAIT_SECONDS),
            ),
        ),
    )
}

@PingogramPreviews
@Composable
private fun EmailCodeScreenResetPendingPreview() {
    AuthScreenPreview(
        AuthPreviewData.state(
            AuthStepUiState.EmailCode(
                emailAddressPattern = "a***@example.org",
                code = "",
                codeLength = PREVIEW_CODE_LENGTH,
                error = DomainError.CodeInvalid,
                reset = EmailResetUiState.Pending(resetInSeconds = PREVIEW_RESET_PENDING_SECONDS),
            ),
        ),
    )
}
