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
import dev.kyralabs.pingogram.core.ui.phone.CountryField
import dev.kyralabs.pingogram.core.ui.phone.CountryPickerSheet
import dev.kyralabs.pingogram.core.ui.phone.CountryUiModel
import dev.kyralabs.pingogram.core.ui.phone.PhoneNumberFields
import dev.kyralabs.pingogram.core.ui.phone.formatNationalNumber
import dev.kyralabs.pingogram.core.ui.phone.numberFormatOf
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.preview.PingogramVariantPreviews
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
        PhoneNumberFields(
            callingCode = state.callingCode,
            number = state.number,
            numberFormat = state.numberFormat,
            error = state.error?.let { errorText(it) },
            onCallingCodeChange = { onEvent(AuthEvent.CallingCodeChanged(it)) },
            onNumberChange = { onEvent(AuthEvent.PhoneNumberChanged(it)) },
            onDone = submit,
        )
    }
    state.countryPicker?.let { picker ->
        CountryPickerSheet(
            state = picker,
            onQueryChange = { onEvent(AuthEvent.CountryQueryChanged(it)) },
            onSelect = { onEvent(AuthEvent.CountrySelected(it)) },
            onDismiss = { onEvent(AuthEvent.CloseCountryPicker) },
        )
    }
}

// Also two panes on a phone in landscape (`docs/DESIGN_SYSTEM.md` §7).
@PingogramPreviews
@PingogramVariantPreviews
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
