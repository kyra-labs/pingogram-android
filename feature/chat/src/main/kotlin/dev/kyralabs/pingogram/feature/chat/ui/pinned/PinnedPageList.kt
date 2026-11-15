package dev.kyralabs.pingogram.feature.chat.ui.pinned

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ChipShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.animateItem
import dev.kyralabs.pingogram.core.ui.component.rememberItemMotion
import dev.kyralabs.pingogram.core.ui.theme.experimentalTypography
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.collections.immutable.ImmutableList

/**
 * The page's one list: the title and subtitle, the filter chips, then [months] (or [states], the loading, empty and
 * error states, in their place) under their headers, each month a segmented group of cards, newest first. Every item
 * is at most `contentMaxWidth` wide and centred, inside [margin].
 */
@Suppress("LongParameterList") // The state, its months or states, the list and its padding, the margin, the events.
@Composable
internal fun PinnedPageList(
    state: PinnedPageUiState,
    months: ImmutableList<PinnedMonth>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    margin: Dp,
    onEvent: (PinnedPageEvent) -> Unit,
    states: (LazyListScope.() -> Unit)? = null,
) {
    val motion = rememberItemMotion()
    val maxWidth = MaterialTheme.sizes.contentMaxWidth
    val item = Modifier.fillMaxWidth().padding(horizontal = margin).widthIn(max = maxWidth)
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = "title", contentType = "title") { PinnedTitle(state, item) }
        // No chips while nothing is pinned, or the pins load or failed.
        if (state.total > 0) {
            item(key = "filters", contentType = "filters") { PinnedFilters(state, margin, onEvent) }
        }
        states?.invoke(this)
        months.forEach { month ->
            item(key = "month:${month.label}", contentType = "month") {
                MonthHeader(month.label, item.animateItem(this, motion))
            }
            month.cards.forEachIndexed { index, card ->
                item(key = card.messageId, contentType = "card") {
                    PinnedCardItem(
                        card = card,
                        shape = ListItemDefaults.segmentedShapes(index, month.cards.size).shape,
                        canUnpin = state.canUnpin,
                        onEvent = onEvent,
                        modifier = item.animateItem(this, motion),
                    )
                }
            }
        }
    }
}

/** "Pinned messages" in `displaySmallWide` over "Pingogram Beta · 23 pinned · newest first" (one heading). */
@Composable
private fun PinnedTitle(
    state: PinnedPageUiState,
    modifier: Modifier,
) {
    val title = state.chatTitle
    val subtitle =
        if (title != null) {
            pluralStringResource(R.plurals.pinned_page_subtitle, state.total, title, state.total)
        } else {
            pluralStringResource(R.plurals.pinned_page_subtitle_no_title, state.total, state.total)
        }
    Column(modifier.padding(bottom = MaterialTheme.spacing.small).semantics(mergeDescendants = true) { heading() }) {
        Text(
            text = stringResource(R.string.pinned_page_title),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.experimentalTypography.displaySmallWide,
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * The filter chips with their counts, one radio group: the selected one a full `primaryContainer` pill, the others
 * outlined with `medium` corners. They scroll sideways where they do not fit.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PinnedFilters(
    state: PinnedPageUiState,
    margin: Dp,
    onEvent: (PinnedPageEvent) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shapes =
        ChipShapes(
            shape = MaterialTheme.shapes.medium,
            selectedShape = CircleShape,
            pressedShape = MaterialTheme.shapes.small,
        )
    val colors =
        FilterChipDefaults.filterChipColors(
            labelColor = scheme.onSurfaceVariant,
            selectedContainerColor = scheme.primaryContainer,
            selectedLabelColor = scheme.onPrimaryContainer,
        )
    val groupLabel = stringResource(R.string.pinned_filters)
    LazyRow(
        modifier = Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = groupLabel },
        contentPadding = PaddingValues(horizontal = margin),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        items(state.filters, key = { it.filter }) { chip ->
            FilterChip(
                selected = chip.filter == state.selected,
                onClick = { onEvent(PinnedPageEvent.SelectFilter(chip.filter)) },
                label = { Text(stringResource(chip.filter.label, chip.count)) },
                modifier = Modifier.semantics { role = Role.RadioButton },
                shapes = shapes,
                colors = colors,
            )
        }
    }
}

/** A month's header in `titleSmallEmphasized` `primary`, a heading. */
@Composable
private fun MonthHeader(
    label: String,
    modifier: Modifier,
) {
    Text(
        text = label,
        modifier =
            modifier
                .padding(top = MaterialTheme.spacing.large, bottom = MaterialTheme.spacing.small)
                .semantics { heading() },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmallEmphasized,
    )
}

/** A chip's label: "All · 23". */
private val PinnedFilter.label: Int
    get() =
        when (this) {
            PinnedFilter.All -> R.string.pinned_filter_all
            PinnedFilter.Text -> R.string.pinned_filter_text
            PinnedFilter.Media -> R.string.pinned_filter_media
            PinnedFilter.Files -> R.string.pinned_filter_files
            PinnedFilter.Links -> R.string.pinned_filter_links
        }
