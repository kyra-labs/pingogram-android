package dev.kyralabs.pingogram.feature.settings.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import dev.kyralabs.pingogram.core.ui.component.LocalRecentCallsLauncher
import dev.kyralabs.pingogram.core.ui.component.tabHeaderActionSize
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Whether settings search is open, and its query, both kept across configuration changes and process death;
 * [focusOnShow] only after the search button opened it, so the keyboard does not come back after a rotation.
 */
@Stable
internal class SettingsSearch(
    val query: TextFieldState,
    private val open: MutableState<Boolean>,
) {
    var focusOnShow by mutableStateOf(false)
        private set

    val isOpen: Boolean get() = open.value

    fun open() {
        focusOnShow = true
        open.value = true
    }

    fun close() {
        focusOnShow = false
        query.clearText()
        open.value = false
    }
}

@Composable
internal fun rememberSettingsSearch(): SettingsSearch {
    val query = rememberTextFieldState()
    val open = rememberSaveable { mutableStateOf(false) }
    return remember(query, open) { SettingsSearch(query, open) }
}

/** The header's search button, a 48 dp tonal circle with its tooltip ("Search settings"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchAction(onClick: () -> Unit) {
    val label = stringResource(R.string.settings_home_search)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        val size = tabHeaderActionSize()
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(size),
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(painter = painterResource(UiR.drawable.ic_search), contentDescription = label)
        }
    }
}

/**
 * Settings search's field in the top bar's place (`docs/DESIGN_SYSTEM.md` §13.9): the search bar's `InputField` in its
 * pill on `surfaceContainerHighest` (the page is `surfaceContainerHigh`), at most `contentMaxWidth` wide inside the
 * window margin, back ("Close search", [onClose]) at its start and a clear button while it holds text. It takes the
 * focus when the search button opened it ([focus]); system back closes it too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSearchBar(
    query: TextFieldState,
    focus: Boolean,
    onClose: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    Surface(
        modifier =
            Modifier
                .statusBarsPadding()
                .padding(horizontal = windowMargin(), vertical = MaterialTheme.spacing.small)
                .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                .fillMaxWidth(),
        shape = SearchBarDefaults.inputFieldShape,
        // The settings page is the search bar's own container color, so the pill takes the next tone up.
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        SearchBarDefaults.InputField(
            textFieldState = query,
            searchBarState = rememberSearchBarState(),
            onSearch = { keyboard?.hide() },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.settings_home_search)) },
            leadingIcon = {
                IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.settings_home_search_close),
                    )
                }
            },
            trailingIcon =
                if (query.text.isNotEmpty()) {
                    {
                        IconButton(onClick = { query.clearText() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_close),
                                contentDescription = stringResource(R.string.settings_home_search_clear),
                            )
                        }
                    }
                } else {
                    null
                },
        )
    }
    if (focus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/**
 * What settings search finds for [query] ([searchEntries] over [SearchEntries]): the entries as the home's rows, in
 * one group, or "No settings found" in `bodyLarge` `onSurfaceVariant` (a polite live region); nothing before a word
 * is typed. A tap opens the entry ([onOpen]).
 */
@Composable
internal fun SettingsSearchResults(
    query: String,
    summaries: SettingsHomeSummaries,
    onOpen: (HomeDestination) -> Unit,
) {
    val resources = LocalResources.current
    val locale = Locale.current.platformLocale
    val hasRecentCalls = LocalRecentCallsLauncher.current != null
    val found =
        remember(query, resources, locale, hasRecentCalls) {
            searchEntries(SearchEntries, query, resources, locale) {
                hasRecentCalls || it.destination != HomeDestination.RecentCalls
            }
        }
    if (query.isBlank()) return
    if (found.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_home_no_results),
            modifier =
                Modifier
                    .padding(horizontal = MaterialTheme.spacing.large)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        return
    }
    SettingsGroup {
        found.forEach { entry ->
            item(key = entry.title) { shapes ->
                HomeEntryRow(
                    entry = entry,
                    index = SearchEntries.indexOf(entry),
                    shapes = shapes,
                    state = HomeRowState(summaries, selection = null),
                    onClick = { onOpen(entry.destination) },
                )
            }
        }
    }
}
