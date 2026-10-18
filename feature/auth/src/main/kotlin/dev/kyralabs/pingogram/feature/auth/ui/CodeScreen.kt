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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AuthCodeType
import dev.kyralabs.pingogram.core.domain.model.isNumeric
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.durationText
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPrimaryButton
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthStepLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthScreenPreview

/**
 * FR-A3: the login code for every delivery type, with the resend countdown, "change number", and the code autofilled
 * or pasted (it submits itself once complete).
 */
@Composable
fun CodeScreen(
    state: AuthStepUiState.Code,
    canSubmit: Boolean,
    pendingAction: AuthAction?,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthStepLayout(
        title = stringResource(R.string.auth_code_title),
        description = codeDescription(state.type, state.phoneNumber),
        modifier = modifier,
        actions = {
            AuthPrimaryButton(
                text = stringResource(R.string.auth_next),
                onClick = { onEvent(AuthEvent.SubmitCode) },
                enabled = canSubmit && state.code.isNotBlank(),
                inProgress = pendingAction == AuthAction.SubmitCode,
            )
            AuthTextButton(
                text = stringResource(R.string.auth_change_number),
                onClick = { onEvent(AuthEvent.ChangePhoneNumber) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        },
    ) {
        TextField(
            value = state.code,
            onValueChange = { onEvent(AuthEvent.CodeChanged(it)) },
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.SmsOtpCode },
            label = { Text(stringResource(R.string.auth_code_label)) },
            isError = state.error != null,
            supportingText = state.error?.let { error -> { Text(errorText(error)) } },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = if (state.type.isNumeric) KeyboardType.NumberPassword else KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { onEvent(AuthEvent.SubmitCode) }),
        )
        if (state.type is AuthCodeType.Fragment) {
            AuthTextButton(
                text = stringResource(R.string.auth_code_open_fragment),
                onClick = { onEvent(AuthEvent.OpenFragment) },
            )
        }
        ResendCode(state = state, canSubmit = canSubmit, pendingAction = pendingAction, onEvent = onEvent)
    }
}

@Composable
private fun ResendCode(
    state: AuthStepUiState.Code,
    canSubmit: Boolean,
    pendingAction: AuthAction?,
    onEvent: (AuthEvent) -> Unit,
) {
    val nextType = state.nextType ?: return
    if (state.canResend) {
        AuthTextButton(
            text = resendText(nextType),
            onClick = { onEvent(AuthEvent.ResendCode) },
            enabled = canSubmit,
            inProgress = pendingAction == AuthAction.ResendCode,
        )
    } else {
        Text(
            text = stringResource(R.string.auth_code_resend_in, durationText(state.resendInSeconds)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun codeDescription(
    type: AuthCodeType,
    phoneNumber: String,
): String =
    when (type) {
        is AuthCodeType.TelegramMessage -> {
            stringResource(R.string.auth_code_sent_telegram)
        }

        is AuthCodeType.Sms -> {
            stringResource(R.string.auth_code_sent_sms, phoneNumber)
        }

        is AuthCodeType.SmsWord -> {
            stringResource(R.string.auth_code_sent_sms_word, phoneNumber) + firstPartHint(type.firstLetter)
        }

        is AuthCodeType.SmsPhrase -> {
            stringResource(R.string.auth_code_sent_sms_phrase, phoneNumber) + firstPartHint(type.firstWord)
        }

        is AuthCodeType.Call -> {
            stringResource(R.string.auth_code_sent_call, phoneNumber)
        }

        is AuthCodeType.FlashCall -> {
            stringResource(R.string.auth_code_sent_flash_call, phoneNumber)
        }

        is AuthCodeType.MissedCall -> {
            pluralStringResource(
                R.plurals.auth_code_sent_missed_call,
                type.length,
                phoneNumber,
                type.phoneNumberPrefix,
                type.length,
            )
        }

        is AuthCodeType.Fragment -> {
            stringResource(R.string.auth_code_sent_fragment, phoneNumber)
        }

        is AuthCodeType.Firebase -> {
            stringResource(R.string.auth_code_sent_unsupported)
        }
    }

@Composable
private fun firstPartHint(start: String): String =
    if (start.isBlank()) "" else " " + stringResource(R.string.auth_code_sent_starts_with, start)

@Composable
private fun resendText(type: AuthCodeType): String =
    when (type) {
        is AuthCodeType.Sms, is AuthCodeType.SmsWord, is AuthCodeType.SmsPhrase, is AuthCodeType.Firebase -> {
            stringResource(R.string.auth_code_resend_sms)
        }

        is AuthCodeType.Call, is AuthCodeType.FlashCall, is AuthCodeType.MissedCall -> {
            stringResource(R.string.auth_code_resend_call)
        }

        is AuthCodeType.TelegramMessage -> {
            stringResource(R.string.auth_code_resend_telegram)
        }

        is AuthCodeType.Fragment -> {
            stringResource(R.string.auth_code_resend_fragment)
        }
    }

private const val PREVIEW_CODE_LENGTH = 5
private const val PREVIEW_RESEND_SECONDS = 42L

private fun codeState(
    type: AuthCodeType = AuthCodeType.Sms(length = PREVIEW_CODE_LENGTH),
    code: String = "",
    resendInSeconds: Long = PREVIEW_RESEND_SECONDS,
    error: DomainError? = null,
) = AuthStepUiState.Code(
    phoneNumber = "+44 7911 123456",
    type = type,
    nextType = AuthCodeType.Call(length = PREVIEW_CODE_LENGTH),
    code = code,
    codeLength = PREVIEW_CODE_LENGTH,
    resendInSeconds = resendInSeconds,
    error = error,
)

@PingogramPreviews
@Composable
private fun CodeScreenPreview() {
    AuthScreenPreview(AuthPreviewData.state(codeState(code = "123")))
}

@PingogramPreviews
@Composable
private fun CodeScreenResendPreview() {
    AuthScreenPreview(
        AuthPreviewData.state(
            codeState(type = AuthCodeType.TelegramMessage(length = PREVIEW_CODE_LENGTH), resendInSeconds = 0),
        ),
    )
}

@PingogramPreviews
@Composable
private fun CodeScreenErrorPreview() {
    AuthScreenPreview(AuthPreviewData.state(codeState(code = "12345", error = DomainError.CodeInvalid)))
}

@PingogramPreviews
@Composable
private fun CodeScreenFragmentPreview() {
    AuthScreenPreview(
        AuthPreviewData.state(
            codeState(type = AuthCodeType.Fragment("https://fragment.com", length = PREVIEW_CODE_LENGTH)),
        ),
    )
}
