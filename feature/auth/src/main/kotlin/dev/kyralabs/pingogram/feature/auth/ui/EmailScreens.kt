package dev.kyralabs.pingogram.feature.auth.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.durationText
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthCodeField
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPrimaryButton
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthStepLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
import dev.kyralabs.pingogram.feature.auth.ui.components.EmailOtpCode
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
            leadingIcon = { Icon(painterResource(R.drawable.ic_mail), contentDescription = null) },
            isError = error != null,
            // Why the address is asked for (board 23i), until an error takes its place.
            supportingText = { Text(error ?: stringResource(R.string.auth_email_supporting)) },
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
        description = emailCodeDescription(state),
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
            label = stringResource(UiR.string.login_code_label),
            error = state.error?.let { errorText(it) },
            onDone = { onEvent(AuthEvent.SubmitEmailCode) },
            contentType = EmailOtpCode,
        )
        OpenEmailAppButton()
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

/**
 * "Open email app" under the code (board 23j): the phone's email app, through Android's chooser of the email category,
 * so the code can be read and the user come back. Where no app handles it, the tap does nothing more.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OpenEmailAppButton() {
    val context = LocalContext.current
    TextButton(
        onClick = {
            val intent =
                Intent
                    .makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                // No email app on the phone: the code still arrives wherever the user reads mail.
            }
        },
        shapes = ButtonDefaults.shapes(),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_open_in_new),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.auth_email_open_app))
    }
}

/** "We sent a 6-digit code to a***@example.org." (board 23j), without the length where Telegram does not give it. */
@Composable
private fun emailCodeDescription(state: AuthStepUiState.EmailCode): String =
    if (state.codeLength > 0) {
        pluralStringResource(
            R.plurals.auth_email_code_description_digits,
            state.codeLength,
            state.codeLength,
            state.emailAddressPattern.bidiIsolated(),
        )
    } else {
        stringResource(R.string.auth_email_code_description, state.emailAddressPattern.bidiIsolated())
    }.bidiIsolated()
