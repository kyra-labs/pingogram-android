package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.auth.R

/**
 * The searchable country list (FR-A2) in a full-height [ModalBottomSheet] (`docs/DESIGN_SYSTEM.md` §6). The sheet
 * handles back and dismissal itself.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CountryPickerSheet(
    state: CountryPickerUiState,
    onEvent: (AuthEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    ModalBottomSheet(
        onDismissRequest = { onEvent(AuthEvent.CloseCountryPicker) },
        sheetState =
            rememberBottomSheetState(
                SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = spacing.windowMarginCompact)) {
            Text(
                text = stringResource(R.string.auth_country_choose),
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.semantics { heading() },
            )
            TextField(
                value = state.query,
                onValueChange = { onEvent(AuthEvent.CountryQueryChanged(it)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.medium),
                label = { Text(stringResource(R.string.auth_country_search)) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Search,
                    ),
            )
        }
        LazyColumn {
            items(state.countries, key = { it.isoCode }, contentType = { CONTENT_TYPE_COUNTRY }) { country ->
                ListItem(
                    onClick = { onEvent(AuthEvent.CountrySelected(country.isoCode)) },
                    modifier = Modifier.animateItem(),
                    leadingContent = {
                        Text(
                            country.flagEmoji,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    },
                    trailingContent = { Text("+${country.callingCode}", style = MaterialTheme.typography.labelLarge) },
                ) {
                    Text(country.name)
                }
            }
            if (state.countries.isEmpty()) {
                item(contentType = CONTENT_TYPE_EMPTY) {
                    Text(
                        text = stringResource(R.string.auth_country_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(spacing.windowMarginCompact),
                    )
                }
            }
        }
    }
}

private const val CONTENT_TYPE_COUNTRY = "country"
private const val CONTENT_TYPE_EMPTY = "empty"
