package dev.kyralabs.pingogram.feature.settings.ui.stickers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.PickerSearchField
import dev.kyralabs.pingogram.feature.settings.ui.SettingsFooter
import dev.kyralabs.pingogram.feature.settings.ui.SettingsListRowStyle
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSectionHeader
import dev.kyralabs.pingogram.feature.settings.ui.settingsListRow

// The search of Settings → Stickers (or its Emoji page): its field above everything, and while it holds words, the
// user's sets that match and Telegram's in place of the page's rows.

/** The search field, first in the list: the search bar's input field on its container, at the rows' width. */
internal fun LazyListScope.stickerSearchField(
    query: String,
    texts: StickerKindTexts,
    style: SettingsListRowStyle,
    onEvent: (StickersEvent) -> Unit,
) {
    item(key = SEARCH_FIELD, contentType = SEARCH_FIELD) {
        Box(Modifier.settingsListRow(this, style)) {
            PickerSearchField(
                query = query,
                placeholder = stringResource(texts.search),
                horizontalPadding = 0.dp,
                onQueryChange = { onEvent(StickersEvent.Search(it)) },
            )
        }
    }
}

/**
 * What [search] found: "Your sets" (the user's own, which a tap opens through [onOpen]), then "On Telegram" (each
 * with "Add", [StickersEvent.AddFound]), a `DelayedLoadingIndicator` while Telegram is asked, its failure with "Try
 * again", or "No sets found" when neither has any; a footer says where the words go.
 */
internal fun LazyListScope.stickerSearchResults(
    search: StickerSearch,
    style: SettingsListRowStyle,
    onEvent: (StickersEvent) -> Unit,
    onOpen: (StickerSetItem) -> Unit,
) {
    val catalog = search.catalog
    val found = (catalog as? StickerCatalog.Found)?.sets.orEmpty()
    if (search.installed.isNotEmpty()) {
        resultHeader(SECTION_YOURS, R.string.settings_stickers_search_yours, style)
        resultRows(search.installed, KEY_YOURS, style, onEvent, onOpen)
    }
    if (found.isNotEmpty()) {
        resultHeader(SECTION_TELEGRAM, R.string.settings_stickers_search_telegram, style)
        resultRows(found, KEY_TELEGRAM, style, onEvent, onOpen)
    }
    item(key = STATUS, contentType = STATUS) {
        Box(Modifier.settingsListRow(this, style).padding(top = MaterialTheme.spacing.medium)) {
            when (catalog) {
                StickerCatalog.Searching -> SearchingIndicator()
                is StickerCatalog.Failed -> SearchFailed(catalog, onRetry = { onEvent(StickersEvent.RetrySearch) })
                is StickerCatalog.Found -> if (found.isEmpty() && search.installed.isEmpty()) NothingFound()
            }
        }
    }
    item(key = FOOTER_SEARCH, contentType = FOOTER_SEARCH) {
        SettingsFooter(
            stringResource(R.string.settings_stickers_search_footer),
            Modifier.settingsListRow(this, style),
        )
    }
}

private fun LazyListScope.resultHeader(
    key: String,
    title: Int,
    style: SettingsListRowStyle,
) {
    item(key = key, contentType = HEADER_SEARCH) {
        SettingsSectionHeader(
            stringResource(title),
            Modifier
                .settingsListRow(this, style)
                .padding(top = MaterialTheme.spacing.large - ListItemDefaults.SegmentedGap),
        )
    }
}

private fun LazyListScope.resultRows(
    sets: List<StickerSetItem>,
    prefix: String,
    style: SettingsListRowStyle,
    onEvent: (StickersEvent) -> Unit,
    onOpen: (StickerSetItem) -> Unit,
) {
    itemsIndexed(sets, key = { _, set -> "$prefix-${set.id}" }, contentType = { _, _ -> ROW_SEARCH }) { index, set ->
        AvailableSetRow(
            set = set,
            shapes = ListItemDefaults.segmentedShapes(index, sets.size),
            onAdd = { onEvent(StickersEvent.AddFound(set.id)) },
            onOpen = { onOpen(set) },
            modifier = Modifier.settingsListRow(this, style),
        )
    }
}

@Composable
private fun SearchingIndicator() {
    val searching = stringResource(R.string.settings_stickers_search_searching)
    Box(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = searching },
        contentAlignment = Alignment.Center,
    ) {
        DelayedLoadingIndicator()
    }
}

@Composable
private fun SearchFailed(
    failure: StickerCatalog.Failed,
    onRetry: () -> Unit,
) {
    val message = errorMessage(LocalResources.current, failure.error, isDebuggable())
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.settings_stickers_search_retry))
        }
    }
}

@Composable
private fun NothingFound() {
    Text(
        text = stringResource(R.string.settings_stickers_search_none),
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge,
    )
}

private const val SEARCH_FIELD = "search-field"
private const val SECTION_YOURS = "search-yours"
private const val SECTION_TELEGRAM = "search-telegram"
private const val KEY_YOURS = "yours"
private const val KEY_TELEGRAM = "telegram"
private const val STATUS = "search-status"
private const val FOOTER_SEARCH = "search-footer"
private const val HEADER_SEARCH = "search-header"
private const val ROW_SEARCH = "search-row"
