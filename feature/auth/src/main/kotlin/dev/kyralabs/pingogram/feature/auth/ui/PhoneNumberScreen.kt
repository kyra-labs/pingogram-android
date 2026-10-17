package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPrimaryButton
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthStepLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthScreenPreview

/**
 * FR-A2: country, calling code and phone number with live formatting; the country picker opens over it. QR code login
 * (FR-A6) is offered as the alternative.
 */
@Composable
fun PhoneNumberScreen(
    state: AuthStepUiState.PhoneNumber,
    canSubmit: Boolean,
    pendingAction: AuthAction?,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val submit = { onEvent(AuthEvent.SubmitPhoneNumber) }
    AuthStepLayout(
        title =
            stringResource(
                if (state.isChangingNumber) R.string.auth_phone_change_title else R.string.auth_phone_title,
            ),
        description = stringResource(R.string.auth_phone_description),
        modifier = modifier,
        actions = {
            AuthPrimaryButton(
                text = stringResource(R.string.auth_next),
                onClick = submit,
                enabled = canSubmit && state.callingCode.isNotEmpty() && state.number.isNotEmpty(),
                inProgress = pendingAction == AuthAction.SubmitPhoneNumber,
            )
            AuthTextButton(
                text = stringResource(R.string.auth_qr_login),
                onClick = { onEvent(AuthEvent.LogInWithQrCode) },
                enabled = canSubmit,
                inProgress = pendingAction == AuthAction.RequestQrCode,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        },
    ) {
        CountryField(country = state.country, onClick = { onEvent(AuthEvent.OpenCountryPicker) })
        PhoneNumberFields(state = state, onEvent = onEvent, onDone = submit)
    }
    state.countryPicker?.let { CountryPickerSheet(state = it, onEvent = onEvent) }
}

@Composable
private fun CountryField(
    country: CountryUiModel?,
    onClick: () -> Unit,
) {
    ListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        overlineContent = { Text(stringResource(R.string.auth_country)) },
        leadingContent =
            country?.let {
                {
                    Text(
                        text = it.flagEmoji,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            },
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Text(country?.name ?: stringResource(R.string.auth_country_choose))
    }
}

@Composable
private fun PhoneNumberFields(
    state: AuthStepUiState.PhoneNumber,
    onEvent: (AuthEvent) -> Unit,
    onDone: () -> Unit,
) {
    val transformation = remember(state.numberFormat) { PhoneNumberVisualTransformation(state.numberFormat) }
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        TextField(
            value = state.callingCode,
            onValueChange = { onEvent(AuthEvent.CallingCodeChanged(it)) },
            modifier = Modifier.weight(CALLING_CODE_WEIGHT).semantics { contentType = ContentType.PhoneCountryCode },
            label = { Text(stringResource(R.string.auth_calling_code)) },
            prefix = { Text("+") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
        )
        TextField(
            value = state.number,
            onValueChange = { onEvent(AuthEvent.PhoneNumberChanged(it)) },
            modifier = Modifier.weight(NUMBER_WEIGHT).semantics { contentType = ContentType.PhoneNumberNational },
            label = { Text(stringResource(R.string.auth_phone_number)) },
            placeholder = state.numberFormat?.let { format -> { Text(format.replace('-', '0')) } },
            isError = state.error != null,
            supportingText = state.error?.let { error -> { Text(errorText(error)) } },
            visualTransformation = transformation,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )
    }
}

private const val CALLING_CODE_WEIGHT = 1f
private const val NUMBER_WEIGHT = 2.5f

/** Shows the national number grouped by TDLib's format ([numberFormatOf]) while the state keeps digits only. */
internal data class PhoneNumberVisualTransformation(
    private val format: String?,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = formatNationalNumber(text.text, format)
        val offsets =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = formatted.transformedOffset(offset)

                override fun transformedToOriginal(offset: Int) = formatted.originalOffset(offset)
            }
        return TransformedText(AnnotatedString(formatted.text), offsets)
    }
}

@PingogramPreviews
@Composable
private fun PhoneNumberScreenPreview() {
    AuthScreenPreview(AuthPreviewData.state(AuthPreviewData.phoneNumber()))
}

@PingogramPreviews
@Composable
private fun PhoneNumberScreenEmptyPreview() {
    AuthScreenPreview(
        AuthPreviewData.state(
            AuthStepUiState.PhoneNumber("", "", null, null, null, isChangingNumber = false, countryPicker = null),
        ),
    )
}

@PingogramPreviews
@Composable
private fun PhoneNumberScreenErrorPreview() {
    AuthScreenPreview(
        AuthPreviewData.state(AuthPreviewData.phoneNumber(number = "791", error = DomainError.PhoneNumberInvalid)),
    )
}

@PingogramPreviews
@Composable
private fun PhoneNumberScreenFloodWaitPreview() {
    AuthScreenPreview(AuthPreviewData.state(AuthPreviewData.phoneNumber(), floodWaitSeconds = 299))
}

@PingogramPreviews
@Composable
private fun PhoneNumberScreenSubmittingOfflinePreview() {
    AuthScreenPreview(
        AuthPreviewData.state(
            AuthPreviewData.phoneNumber(isChangingNumber = true),
            connection = ConnectionStatus.WaitingForNetwork,
            pendingAction = AuthAction.SubmitPhoneNumber,
        ),
    )
}

@PingogramPreviews
@Composable
private fun CountryPickerPreview() {
    AuthScreenPreview(AuthPreviewData.state(AuthPreviewData.phoneNumber(pickerOpen = true)))
}
