package dev.kyralabs.pingogram.core.ui.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.domain.model.Country
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.drop

/** A country in the phone number field and the country picker. */
@Immutable
data class CountryUiModel(
    val isoCode: String,
    val name: String,
    val flagEmoji: String,
    /** The main calling code, without `+`. */
    val callingCode: String,
)

/**
 * The open country picker: [countries] are the visible countries matching [query]; [selectedIsoCode] is the country
 * of the phone number field, shown selected.
 */
@Immutable
data class CountryPickerUiState(
    val query: String,
    val countries: ImmutableList<CountryUiModel>,
    val selectedIsoCode: String? = null,
)

fun Country.toUiModel() = CountryUiModel(isoCode, name, flagEmoji, callingCodes.firstOrNull().orEmpty())

/** The listed countries matching a picker query: by name, English name, ISO code or calling code. */
fun List<Country>.matching(query: String): ImmutableList<CountryUiModel> =
    query.trim().removePrefix("+").let { term ->
        asSequence()
            .filter { !it.isHidden }
            .filter { country ->
                term.isEmpty() ||
                    country.name.contains(term, ignoreCase = true) ||
                    country.englishName.contains(term, ignoreCase = true) ||
                    country.isoCode.equals(term, ignoreCase = true) ||
                    country.callingCodes.any { it.startsWith(term) }
            }.map { it.toUiModel() }
            .toImmutableList()
    }

/**
 * The searchable country list (FR-A2) in a full-height [ModalBottomSheet] (`docs/DESIGN_SYSTEM.md` §6): the search
 * bar's input field on its container, then the countries, the one of the phone number field selected and scrolled
 * into view. The sheet handles back and dismissal itself ([onDismiss]); a typed query goes to [onQueryChange], a
 * picked country's ISO code to [onSelect].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(
    state: CountryPickerUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (isoCode: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState =
            rememberBottomSheetState(
                SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = spacing.windowMarginCompact)) {
            Text(
                text = stringResource(R.string.phone_country_choose),
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.semantics { heading() },
            )
            CountrySearchField(query = state.query, onQueryChange = onQueryChange)
        }
        val motion = MaterialTheme.motionScheme
        val listState =
            rememberLazyListState(
                initialFirstVisibleItemIndex =
                    state.countries.indexOfFirst { it.isoCode == state.selectedIsoCode }.coerceAtLeast(0),
            )
        LazyColumn(state = listState) {
            items(state.countries, key = { it.isoCode }, contentType = { CONTENT_TYPE_COUNTRY }) { country ->
                CountryItem(
                    country = country,
                    selected = country.isoCode == state.selectedIsoCode,
                    onClick = { onSelect(country.isoCode) },
                    // Filtering moves rows: placement on the default spatial spring, fades on the effects springs (§5).
                    modifier =
                        Modifier.animateItem(
                            fadeInSpec = motion.defaultEffectsSpec(),
                            placementSpec = motion.defaultSpatialSpec(),
                            fadeOutSpec = motion.fastEffectsSpec(),
                        ),
                )
            }
            if (state.countries.isEmpty()) {
                item(contentType = CONTENT_TYPE_EMPTY) {
                    Text(
                        text = stringResource(R.string.phone_country_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(spacing.windowMarginCompact),
                    )
                }
            }
        }
    }
}

/**
 * The search bar's input field (`SearchBarDefaults.InputField`) on the search bar's container, with a clear button
 * while there is text. It keeps its own text, which the view model filters by.
 */
@Composable
private fun CountrySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val text = rememberTextFieldState(query)
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    LaunchedEffect(text) { snapshotFlow { text.text.toString() }.drop(1).collect { currentOnQueryChange(it) } }
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.medium),
        shape = SearchBarDefaults.inputFieldShape,
        color = SearchBarDefaults.colors().containerColor,
    ) {
        SearchBarDefaults.InputField(
            textFieldState = text,
            searchBarState = rememberSearchBarState(),
            onSearch = { keyboard?.hide() },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.phone_country_search)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon =
                if (text.text.isNotEmpty()) {
                    {
                        IconButton(onClick = { text.clearText() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.phone_country_search_clear),
                            )
                        }
                    }
                } else {
                    null
                },
            keyboardOptions =
                KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
        )
    }
}

/**
 * A country: its flag (decorative), name and calling code. The selected one is the list item's selected container
 * (`secondaryContainer`, `large`), inset from the sheet's sides like the chat list's rows (`docs/DESIGN_SYSTEM.md`
 * §4); the others sit on the sheet's own surface, their pressed and focused states shown as state layers.
 */
@Composable
private fun CountryItem(
    country: CountryUiModel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val insets = rowInsets()
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier.padding(insets),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        contentPadding = insetContentPadding(insets),
        leadingContent = {
            Text(
                country.flagEmoji,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clearAndSetSemantics {},
            )
        },
        // Left to right in every layout: "+44", never "44+".
        trailingContent = {
            Text(
                "+${country.callingCode}",
                style = MaterialTheme.typography.labelLarge.numericContent,
            )
        },
    ) {
        Text(country.name)
    }
}

/** A row's inset in the sheet: `spacing.small` from its sides, half the segmented gap from the rows around. */
@Composable
private fun rowInsets(): PaddingValues =
    PaddingValues(horizontal = MaterialTheme.spacing.small, vertical = ListItemDefaults.SegmentedGap / 2)

/** The list item's content padding less [insets], so a row's content stays where the list item puts it. */
private fun insetContentPadding(insets: PaddingValues): PaddingValues {
    val padding = ListItemDefaults.ContentPadding
    val direction = LayoutDirection.Ltr
    return PaddingValues(
        start = padding.calculateStartPadding(direction) - insets.calculateStartPadding(direction),
        top = padding.calculateTopPadding() - insets.calculateTopPadding(),
        end = padding.calculateEndPadding(direction) - insets.calculateEndPadding(direction),
        bottom = padding.calculateBottomPadding() - insets.calculateBottomPadding(),
    )
}

private const val CONTENT_TYPE_COUNTRY = "country"
private const val CONTENT_TYPE_EMPTY = "empty"
