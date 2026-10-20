package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPrimaryButton
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthStepLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
import dev.kyralabs.pingogram.feature.auth.ui.components.fieldError
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthScreenPreview
import dev.kyralabs.pingogram.feature.auth.ui.preview.LANDSCAPE_PHONE
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * FR-A2: country, calling code and phone number with live formatting; the country picker opens over it. QR code login
 * (FR-A6) is offered as the alternative.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        badgeShape = MaterialShapes.Cookie7Sided,
        badgeIcon = UiR.drawable.ic_contacts_filled,
        description = stringResource(R.string.auth_phone_description),
        isError = state.error != null,
        modifier = modifier,
        primaryAction = {
            AuthPrimaryButton(
                text = stringResource(R.string.auth_next),
                onClick = submit,
                enabled = canSubmit && state.callingCode.isNotEmpty() && state.number.isNotEmpty(),
                inProgress = pendingAction == AuthAction.SubmitPhoneNumber,
            )
        },
        secondaryAction = {
            AuthTextButton(
                text = stringResource(R.string.auth_qr_login),
                onClick = { onEvent(AuthEvent.LogInWithQrCode) },
                enabled = canSubmit,
                inProgress = pendingAction == AuthAction.RequestQrCode,
            )
        },
    ) {
        CountryField(
            country = state.country,
            isPickerOpen = state.countryPicker != null,
            onOpenPicker = { onEvent(AuthEvent.OpenCountryPicker) },
        )
        PhoneNumberFields(state = state, onEvent = onEvent, onDone = submit)
    }
    state.countryPicker?.let { CountryPickerSheet(state = it, onEvent = onEvent) }
}

/**
 * The country as a read-only filled text field with the exposed dropdown arrow (`docs/DESIGN_SYSTEM.md` §6: text
 * fields), like the number fields beside it. Tapping it, or activating it with a screen reader, opens the country
 * picker sheet instead of a menu; the arrow turns while the sheet is open.
 */
@Composable
private fun CountryField(
    country: CountryUiModel?,
    isPickerOpen: Boolean,
    onOpenPicker: () -> Unit,
) {
    ExposedDropdownMenuBox(expanded = isPickerOpen, onExpandedChange = { if (it) onOpenPicker() }) {
        TextField(
            value = country?.name.orEmpty(),
            onValueChange = {},
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            label = { Text(stringResource(R.string.auth_country)) },
            placeholder = { Text(stringResource(R.string.auth_country_choose)) },
            leadingIcon =
                country?.let {
                    {
                        Text(
                            text = it.flagEmoji,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isPickerOpen) },
            singleLine = true,
        )
    }
}

@Composable
private fun PhoneNumberFields(
    state: AuthStepUiState.PhoneNumber,
    onEvent: (AuthEvent) -> Unit,
    onDone: () -> Unit,
) {
    val transformation = remember(state.numberFormat) { PhoneNumberVisualTransformation(state.numberFormat) }
    // A phone number reads left to right in every language: "+44 7911 123456", the calling code first.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        PhoneNumberRow(state, transformation, onEvent, onDone)
    }
}

@Composable
private fun PhoneNumberRow(
    state: AuthStepUiState.PhoneNumber,
    transformation: PhoneNumberVisualTransformation,
    onEvent: (AuthEvent) -> Unit,
    onDone: () -> Unit,
) {
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
        val error = state.error?.let { errorText(it) }
        TextField(
            value = state.number,
            onValueChange = { onEvent(AuthEvent.PhoneNumberChanged(it)) },
            modifier =
                Modifier
                    .weight(NUMBER_WEIGHT)
                    .fieldError(error)
                    .semantics { contentType = ContentType.PhoneNumberNational },
            label = { Text(stringResource(R.string.auth_phone_number)) },
            placeholder = state.numberFormat?.let { format -> { Text(format.replace('-', '0')) } },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
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

// Also two panes on a phone in landscape (`docs/DESIGN_SYSTEM.md` §7).
@PingogramPreviews
@Preview(name = "Landscape", device = LANDSCAPE_PHONE)
@Composable
private fun PhoneNumberScreenPreview() {
    AuthScreenPreview(AuthPreviewData.phoneNumber)
}

@PingogramPreviews
@Composable
private fun PhoneNumberScreenEmptyPreview() {
    AuthScreenPreview(AuthPreviewData.phoneNumberEmpty)
}

@PingogramPreviews
@Composable
private fun PhoneNumberScreenErrorPreview() {
    AuthScreenPreview(AuthPreviewData.phoneNumberError)
}

@PingogramPreviews
@Composable
private fun PhoneNumberScreenFloodWaitPreview() {
    AuthScreenPreview(AuthPreviewData.phoneNumberFloodWait)
}

@PingogramPreviews
@Composable
private fun PhoneNumberScreenSubmittingOfflinePreview() {
    AuthScreenPreview(AuthPreviewData.phoneNumberSubmittingOffline)
}

@PingogramPreviews
@Composable
private fun CountryPickerPreview() {
    AuthScreenPreview(AuthPreviewData.countryPicker)
}
