package dev.kyralabs.pingogram.core.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * The country as a read-only text field with the exposed dropdown arrow (`docs/DESIGN_SYSTEM.md` §6: text fields),
 * like the number fields beside it ([shape] and [colors]: the filled style at sign-in, the rounded tonal one in
 * Settings). Tapping it, or activating it with a screen reader, opens the country picker sheet ([onOpenPicker])
 * instead of a menu; the arrow turns while the sheet is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryField(
    country: CountryUiModel?,
    isPickerOpen: Boolean,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    ExposedDropdownMenuBox(
        expanded = isPickerOpen,
        onExpandedChange = { if (it) onOpenPicker() },
        modifier = modifier,
    ) {
        TextField(
            value = country?.name.orEmpty(),
            onValueChange = {},
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            label = { Text(stringResource(R.string.phone_country)) },
            placeholder = { Text(stringResource(R.string.phone_country_choose)) },
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
            shape = shape,
            colors = colors,
        )
    }
}

/**
 * The calling code ("+44") and the national number side by side, left to right in every language, the number grouped
 * by TDLib's [numberFormat] as it is typed ([PhoneNumberVisualTransformation]) while the state keeps digits only, with
 * the phone autofill hints; [error] below the number, reported to screen readers as its error. Done on the keyboard
 * calls [onDone].
 */
@Suppress("LongParameterList") // The two fields' values, the format and error, where changes go, and the style.
@Composable
fun PhoneNumberFields(
    callingCode: String,
    number: String,
    numberFormat: String?,
    error: String?,
    onCallingCodeChange: (String) -> Unit,
    onNumberChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    val transformation = remember(numberFormat) { PhoneNumberVisualTransformation(numberFormat) }
    // A phone number reads left to right in every language: "+44 7911 123456", the calling code first.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            TextField(
                value = callingCode,
                onValueChange = onCallingCodeChange,
                modifier =
                    Modifier.weight(CALLING_CODE_WEIGHT).semantics { contentType = ContentType.PhoneCountryCode },
                label = { Text(stringResource(R.string.phone_calling_code)) },
                prefix = { Text("+") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                shape = shape,
                colors = colors,
            )
            TextField(
                value = number,
                onValueChange = onNumberChange,
                modifier =
                    Modifier
                        .weight(NUMBER_WEIGHT)
                        .semantics {
                            contentType = ContentType.PhoneNumberNational
                            if (error != null) error(error)
                        },
                label = { Text(stringResource(R.string.phone_number)) },
                placeholder = numberFormat?.let { format -> { Text(format.replace('-', '0')) } },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                visualTransformation = transformation,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                shape = shape,
                colors = colors,
            )
        }
    }
}

private const val CALLING_CODE_WEIGHT = 1f
private const val NUMBER_WEIGHT = 2.5f
