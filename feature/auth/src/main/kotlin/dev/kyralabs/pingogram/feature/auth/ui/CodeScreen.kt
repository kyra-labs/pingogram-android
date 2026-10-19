package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import dev.kyralabs.pingogram.core.domain.model.AuthCodeType
import dev.kyralabs.pingogram.core.domain.model.isNumeric
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.durationText
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthCodeField
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPrimaryButton
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthStepLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
import dev.kyralabs.pingogram.feature.auth.ui.components.announcedCountdownSeconds
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthScreenPreview
import dev.kyralabs.pingogram.feature.auth.ui.preview.LANDSCAPE_PHONE
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * FR-A3: the login code for every delivery type, with the resend countdown, "change number", and the code autofilled
 * or pasted (it submits itself once complete).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        badgeShape = MaterialShapes.Sunny,
        badgeIcon = UiR.drawable.ic_chat_filled,
        description = codeDescription(state.type, state.phoneNumber),
        isError = state.error != null,
        modifier = modifier,
        primaryAction = {
            AuthPrimaryButton(
                text = stringResource(R.string.auth_next),
                onClick = { onEvent(AuthEvent.SubmitCode) },
                enabled = canSubmit && state.code.isNotBlank(),
                inProgress = pendingAction == AuthAction.SubmitCode,
            )
        },
        secondaryAction = {
            AuthTextButton(
                text = stringResource(R.string.auth_change_number),
                onClick = { onEvent(AuthEvent.ChangePhoneNumber) },
            )
        },
    ) {
        AuthCodeField(
            value = state.code,
            onValueChange = { onEvent(AuthEvent.CodeChanged(it)) },
            label = stringResource(R.string.auth_code_label),
            error = state.error?.let { errorText(it) },
            onDone = { onEvent(AuthEvent.SubmitCode) },
            isNumeric = state.type.isNumeric,
            contentType = ContentType.SmsOtpCode,
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

/**
 * The resend countdown, then the button for a new code by [AuthStepUiState.Code.nextType]. A polite live region:
 * screen readers hear the countdown at most every 15 seconds ([announcedCountdownSeconds]) and the button once it
 * replaces it.
 */
@Composable
private fun ResendCode(
    state: AuthStepUiState.Code,
    canSubmit: Boolean,
    pendingAction: AuthAction?,
    onEvent: (AuthEvent) -> Unit,
) {
    val nextType = state.nextType ?: return
    Box(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        if (state.canResend) {
            AuthTextButton(
                text = resendText(nextType),
                onClick = { onEvent(AuthEvent.ResendCode) },
                enabled = canSubmit,
                inProgress = pendingAction == AuthAction.ResendCode,
            )
        } else {
            val announcement =
                stringResource(
                    R.string.auth_code_resend_in,
                    durationText(announcedCountdownSeconds(state.resendInSeconds)),
                )
            Text(
                text = stringResource(R.string.auth_code_resend_in, durationText(state.resendInSeconds)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics { contentDescription = announcement },
            )
        }
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

// Also two panes on a phone in landscape (`docs/DESIGN_SYSTEM.md` §7).
@PingogramPreviews
@Preview(name = "Landscape", device = LANDSCAPE_PHONE)
@Composable
private fun CodeScreenPreview() {
    AuthScreenPreview(AuthPreviewData.code)
}

@PingogramPreviews
@Composable
private fun CodeScreenResendPreview() {
    AuthScreenPreview(AuthPreviewData.codeResend)
}

@PingogramPreviews
@Composable
private fun CodeScreenErrorPreview() {
    AuthScreenPreview(AuthPreviewData.codeError)
}

@PingogramPreviews
@Composable
private fun CodeScreenFragmentPreview() {
    AuthScreenPreview(AuthPreviewData.codeFragment)
}

@PingogramPreviews
@Composable
private fun CodeScreenPhrasePreview() {
    AuthScreenPreview(AuthPreviewData.codePhrase)
}
