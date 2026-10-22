package dev.kyralabs.pingogram.feature.profile.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.RoundedPolygon
import dev.kyralabs.pingogram.core.domain.model.SharedMediaKind
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.fadingEdges
import dev.kyralabs.pingogram.core.ui.component.rememberTabScroll
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import dev.kyralabs.pingogram.core.ui.R as UiR

// The shared media of a profile (REQUIREMENTS.md FR-P2, docs/DESIGN_SYSTEM.md §6 "Shared media"): the tabs, then the
// shown tab's items in the same lazy grid as the rest of the page (photos, videos and GIFs a cell each, files, songs,
// voice messages and links a full-width segmented row each), keyed by message and typed by kind.

/**
 * The shared media's items for a [LazyGridScope] whose cells are [cellSize] wide ([cellSizePx] in pixels, the size the
 * grid's images are decoded at): the tabs ([SharedMediaTabRow]) as a full-span item, then the shown tab's content,
 * inset by [margin] from the grid's sides. Nothing while [media] has no chat yet. An empty tab says [emptyBody] below
 * its title: what shows there, for a private chat or a group.
 */
@Suppress("LongParameterList") // The state, where its events go, and the grid's geometry.
internal fun LazyGridScope.sharedMediaItems(
    media: SharedMediaUiState,
    onEvent: (SharedMediaEvent) -> Unit,
    actions: SharedMediaActions,
    cellSizePx: Int,
    margin: Dp,
    showTabs: Boolean = true,
    @StringRes emptyBody: Int = R.string.profile_media_empty_body,
) {
    val chatId = media.chatId ?: return
    if (showTabs) {
        fullSpan(key = TABS, contentType = TABS) {
            SharedMediaTabRow(media.selected, onSelect = { onEvent(SharedMediaEvent.Select(it)) }, margin = margin)
        }
    }
    when (val content = media.content) {
        null, SharedMediaContent.Loading -> {
            fullSpan(key = STATE, contentType = STATE) { MediaLoading() }
        }

        SharedMediaContent.Empty -> {
            fullSpan(key = STATE, contentType = STATE) { MediaEmpty(media.selected, emptyBody) }
        }

        is SharedMediaContent.Error -> {
            fullSpan(key = STATE, contentType = STATE) {
                MediaError(content, onRetry = { onEvent(SharedMediaEvent.Retry) })
            }
        }

        is SharedMediaContent.Items -> {
            itemsWithMore(content, chatId, onEvent, actions, cellSizePx, margin)
        }
    }
}

@Suppress("LongParameterList")
private fun LazyGridScope.itemsWithMore(
    content: SharedMediaContent.Items,
    chatId: Long,
    onEvent: (SharedMediaEvent) -> Unit,
    actions: SharedMediaActions,
    cellSizePx: Int,
    margin: Dp,
) {
    val items = content.items
    val rows = items.count { it !is SharedMediaItem.Media }
    var rowIndex = 0
    val rowIndices = items.map { if (it is SharedMediaItem.Media) -1 else rowIndex++ }
    itemsIndexed(
        items = items,
        key = { _, item -> item.messageId },
        span = { _, item -> if (item is SharedMediaItem.Media) GridItemSpan(1) else GridItemSpan(maxLineSpan) },
        contentType = { _, item -> item.contentType },
    ) { index, item ->
        when (item) {
            is SharedMediaItem.Media -> {
                MediaCell(item, cellSizePx, onClick = { actions.onOpenMedia(chatId, item.messageId) })
            }

            is SharedMediaItem.File -> {
                FileRow(
                    item = item,
                    shapes = ListItemDefaults.segmentedShapes(rowIndices[index], rows),
                    onClick = { actions.onOpenMessage(chatId, item.messageId) },
                    modifier = Modifier.padding(horizontal = margin).animateItem(),
                )
            }

            is SharedMediaItem.Link -> {
                LinkRow(
                    item = item,
                    shapes = ListItemDefaults.segmentedShapes(rowIndices[index], rows),
                    onClick = { actions.onOpenUrl(item.url) },
                    modifier = Modifier.padding(horizontal = margin).animateItem(),
                )
            }
        }
    }
    when (val more = content.more) {
        MoreMedia.Loading -> {
            fullSpan(key = MORE, contentType = MORE) { MediaLoading() }
        }

        is MoreMedia.Failed -> {
            fullSpan(key = MORE, contentType = MORE) {
                MoreError(more, onRetry = { onEvent(SharedMediaEvent.LoadMore) })
            }
        }

        MoreMedia.None, MoreMedia.Available -> {}
    }
}

/**
 * While the shown tab has [MoreMedia.Available], asks for the next page ([SharedMediaEvent.LoadMore]) when the grid's
 * end comes within [LOAD_MORE_THRESHOLD] items (`docs/PERFORMANCE.md` §3), and again whenever items were added and the
 * end is still near. Read from the grid's layout, outside its items.
 */
@Composable
internal fun SharedMediaLoadMoreEffect(
    gridState: LazyGridState,
    media: SharedMediaUiState,
    onEvent: (SharedMediaEvent) -> Unit,
) {
    val canLoadMore = (media.content as? SharedMediaContent.Items)?.more == MoreMedia.Available
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(gridState, canLoadMore, media.selected) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow {
            val layout = gridState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount.takeIf { it > 0 && lastVisible >= it - 1 - LOAD_MORE_THRESHOLD }
        }.filterNotNull()
            .distinctUntilChanged()
            .collect { currentOnEvent(SharedMediaEvent.LoadMore) }
    }
}

private fun LazyGridScope.fullSpan(
    key: Any,
    contentType: Any,
    content: @Composable LazyGridItemScope.() -> Unit,
) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = contentType, content = content)
}

/** The content type of an item's composable: a grid cell or a row of one kind. */
private val SharedMediaItem.contentType: String
    get() =
        when (this) {
            is SharedMediaItem.Media -> "media"
            is SharedMediaItem.File -> "file"
            is SharedMediaItem.Link -> "link"
        }

private const val TABS = "media-tabs"
private const val STATE = "media-state"
private const val MORE = "media-more"

/** How near the grid's end the next page loads (`docs/PERFORMANCE.md` §3). */
private const val LOAD_MORE_THRESHOLD = 20
