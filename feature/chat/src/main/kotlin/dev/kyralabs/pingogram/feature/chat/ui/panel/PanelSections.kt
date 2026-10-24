package dev.kyralabs.pingogram.feature.chat.ui.panel

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.domain.model.EmojiCategory
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.PanelPreviewData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Reports the sets whose sections are on screen or next below it and still have placeholders, whenever that changes,
 * so their stickers load before they scroll into view.
 */
@Composable
internal fun ReportVisibleSets(
    gridState: LazyGridState,
    sections: ImmutableList<StickerSection>,
    starts: IntArray,
    onEvent: (EmojiPanelEvent) -> Unit,
) {
    LaunchedEffect(gridState, sections, starts) {
        snapshotFlow {
            val visible = gridState.layoutInfo.visibleItemsInfo
            val first = sectionAt(starts, visible.firstOrNull()?.index ?: 0)
            val last = (sectionAt(starts, visible.lastOrNull()?.index ?: 0) + 1).coerceAtMost(sections.lastIndex)
            (first..last).mapNotNull { index ->
                val section = sections[index]
                (section.kind as? StickerSectionKind.Set)?.setId?.takeIf { section.placeholders > 0 }
            }
        }.distinctUntilChanged().collect { setIds ->
            if (setIds.isNotEmpty()) onEvent(EmojiPanelEvent.SetsVisible(setIds))
        }
    }
}

/**
 * The row of a tab's [count] sections above its grid: one icon toggle button each (Material 3 Expressive's shape
 * morph, `secondaryContainer` when selected), the section of the grid's topmost row selected and kept in view. A tap
 * jumps the grid to that section. Tabs for screen readers, named by [description].
 */
@Composable
internal fun SectionRow(
    count: Int,
    gridState: LazyGridState,
    starts: IntArray,
    description: @Composable (Int) -> String,
    icon: @Composable (Int) -> Unit,
) {
    val selected by remember(gridState, starts) {
        derivedStateOf { sectionAt(starts, gridState.firstVisibleItemIndex) }
    }
    val rowState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(rowState, selected) {
        val visible = rowState.layoutInfo.visibleItemsInfo
        val fullyVisible =
            visible.any {
                it.index == selected && it.offset >= 0 &&
                    it.offset + it.size <= rowState.layoutInfo.viewportEndOffset
            }
        if (!fullyVisible) rowState.scrollToItem((selected - 1).coerceAtLeast(0))
    }
    val colors =
        IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    LazyRow(
        state = rowState,
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.small),
    ) {
        items(count, key = { it }) { index ->
            val select = {
                scope.launch { gridState.scrollToItem(starts[index]) }
                Unit
            }
            Box(Modifier.panelTab(description(index), selected = index == selected, onSelect = select)) {
                IconToggleButton(
                    checked = index == selected,
                    onCheckedChange = { select() },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    colors = colors,
                ) { icon(index) }
            }
        }
    }
}

/** A decorative icon of [SectionRow]: its button is named for screen readers. */
@Composable
internal fun PanelIcon(
    @DrawableRes icon: Int,
) {
    Icon(painterResource(icon), contentDescription = null)
}

/** A section's title in the grid: `titleSmallEmphasized` in `onSurfaceVariant`, a heading for screen readers. */
@Composable
internal fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.spacing.small,
                    end = MaterialTheme.spacing.small,
                    top = MaterialTheme.spacing.medium,
                    bottom = MaterialTheme.spacing.small,
                ).semantics { heading() },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.titleSmallEmphasized,
    )
}

/** The index of the first item of each section in a grid with a header per section and [sizes] items after it. */
internal fun sectionStarts(sizes: List<Int>): IntArray {
    var next = 0
    return IntArray(sizes.size) { index -> next.also { next += 1 + sizes[index] } }
}

/** The section that grid item [index] belongs to, from its [starts]; 0 without sections. */
internal fun sectionAt(
    starts: IntArray,
    index: Int,
): Int = starts.indexOfLast { it <= index }.coerceAtLeast(0)
