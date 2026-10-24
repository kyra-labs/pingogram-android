package dev.kyralabs.pingogram.feature.auth.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import dev.kyralabs.pingogram.core.ui.component.CountdownIndicator
import dev.kyralabs.pingogram.core.ui.phone.loginCodeDescription
import dev.kyralabs.pingogram.core.ui.phone.loginCodeResendText
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.durationText
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.time.announcedCountdownSeconds
import dev.kyralabs.pingogram.core.ui.time.secondsLeft
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthCodeField
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPrimaryButton
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthStepLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
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
        description = loginCodeDescription(state.type, state.phoneNumber.bidiIsolated()).bidiIsolated(),
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
            label = stringResource(UiR.string.login_code_label),
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
 * The resend countdown, its progress running down beside it ([CountdownIndicator]), then the button for a new code by
 * [AuthStepUiState.Code.nextType]. A polite live region: screen readers hear the countdown at most every 15 seconds
 * ([announcedCountdownSeconds]) and the button once it replaces it.
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
                text = loginCodeResendText(nextType),
                onClick = { onEvent(AuthEvent.ResendCode) },
                enabled = canSubmit,
                inProgress = pendingAction == AuthAction.ResendCode,
            )
        } else {
            val announcement =
                stringResource(
                    UiR.string.login_code_resend_in,
                    durationText(announcedCountdownSeconds(state.resendInSeconds)),
                )
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CountdownIndicator(secondsLeft = state.resendInSeconds, totalSeconds = state.resendTotalSeconds)
                Text(
                    text = stringResource(UiR.string.login_code_resend_in, durationText(state.resendInSeconds)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = announcement },
                )
            }
        }
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
