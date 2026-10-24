package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.os.ConfigurationCompat
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.formatPhoneNumber
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.feature.settings.R

// The parts of Edit profile about the account rather than the profile: its phone number (FR-S1, Account).

/**
 * The account's [phoneNumber] (formatted, left to right) in a row of its own, ending in a "Change" [TextButton] that
 * opens Change number ([onChangeNumber]), named with the number for screen readers. Nothing while the number is
 * unknown.
 */
@Composable
internal fun PhoneNumberSection(
    phoneNumber: String,
    onChangeNumber: () -> Unit,
) {
    if (phoneNumber.isEmpty()) return
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
    val formatted = formatPhoneNumber(phoneNumber, locale?.country.orEmpty())
    FieldGroup(title = stringResource(R.string.settings_section_profile_phone)) {
        val label = stringResource(R.string.settings_edit_profile_change_number_label, formatted.bidiIsolated())
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
            colors = settingsRowColors(),
            trailingContent = {
                TextButton(
                    onClick = onChangeNumber,
                    modifier = Modifier.semantics { contentDescription = label },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.settings_edit_profile_change_number))
                }
            },
        ) {
            Text(formatted, style = MaterialTheme.typography.bodyLarge.numericContent)
        }
    }
}
