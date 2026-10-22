package dev.kyralabs.pingogram.feature.chat.ui.inline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.InlineResultsButton
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.LoadingIndicatorDelay
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.InlineBotEvent
import dev.kyralabs.pingogram.feature.chat.ui.bot.isHardwareEnter
import dev.kyralabs.pingogram.feature.chat.ui.composer.ScheduleDialog
import dev.kyralabs.pingogram.feature.chat.ui.content.PreviewImages
import dev.kyralabs.pingogram.feature.chat.ui.preview.InlinePreviewData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.time.Instant
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * An inline bot's results above the composer (`docs/DESIGN_SYSTEM.md` §6 "Inline bot results"), where the "/"
 * suggestions show: one `surfaceContainer` panel (the pill's color) with `large` corners, inset `spacing.small` from
 * the sides as the pill is, at most [WINDOW_FRACTION] of the window tall, scrolling beyond ([InlineResultsPanel]).
 * Nothing while not [enabled] (recording).
 *
 * It grows in from the bottom on the motion scheme's default spatial spring with an effects-spring fade and shrinks
 * away once the field holds no inline query (the last results stay while it leaves). A hardware keyboard picks a result
 * with the down and up arrows and sends it with Enter; Escape hides the results until the query changes
 * ([InlineResultsState]).
 */
@Composable
internal fun InlineResultsArea(
    inline: InlineBotInput,
    state: InlineResultsState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    now: () -> Instant = Instant::now,
) {
    val bot = inline.state.takeIf { enabled }
    val motionScheme = MaterialTheme.motionScheme
    var shown by remember { mutableStateOf(bot) }
    val visible = state.isShown(bot)
    if (visible) shown = bot
    LaunchedEffect(bot?.queryKey) { state.onQuery(bot?.queryKey) }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter =
            expandVertically(motionScheme.defaultSpatialSpec(), expandFrom = Alignment.Bottom) +
                fadeIn(motionScheme.defaultEffectsSpec()),
        exit =
            shrinkVertically(motionScheme.fastSpatialSpec(), shrinkTowards = Alignment.Bottom) +
                fadeOut(motionScheme.fastEffectsSpec()),
    ) {
        shown?.let { InlineResultsPanel(it, state.highlighted, inline.canSchedule, inline.onEvent, now = now) }
    }
}

/**
 * The panel of [bot]'s answer: a loading indicator after a moment ([DelayedLoadingIndicator]); the results
 * ([ResultsGrid]), with the result at [highlighted] picked by a hardware keyboard (-1: none); "No results"; or the
 * error with "Try again" in `errorContainer`. The bot's button heads the results and "No results". While a newer query
 * is on its way, a wavy indicator runs along the panel's top edge after a moment, taking no room. A result scheduled
 * asks for its time in the composer's [ScheduleDialog]. Screen readers hear the bot's name and how many results there
 * are as the pane's title.
 */
@Composable
internal fun InlineResultsPanel(
    bot: InlineBotUiState,
    highlighted: Int,
    canSchedule: Boolean,
    onEvent: (InlineBotEvent) -> Unit,
    modifier: Modifier = Modifier,
    now: () -> Instant = Instant::now,
) {
    val spacing = MaterialTheme.spacing
    var scheduling by remember { mutableStateOf<InlineResultEntry?>(null) }
    val actions =
        remember(canSchedule, onEvent) {
            InlineSendActions(
                canSchedule = canSchedule,
                onSend = { entry, silent ->
                    onEvent(InlineBotEvent.Send(entry.queryId, entry.result.id, SendOptions(silent = silent)))
                },
                onSchedule = { scheduling = it },
            )
        }
    val title = panelTitle(bot)
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = spacing.small, top = spacing.small, end = spacing.small)
                .heightIn(max = maxPanelHeight())
                .semantics { paneTitle = title }
                .testTag(INLINE_RESULTS_TEST_TAG),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box {
            when (val content = bot.content) {
                InlineResultsContent.Loading -> LoadingContent()
                is InlineResultsContent.Results -> ResultsGrid(bot, content, highlighted, actions, onEvent)
                is InlineResultsContent.Empty -> EmptyContent(content.button, onEvent)
                is InlineResultsContent.Failed -> FailedContent(content, onEvent)
            }
            SearchingIndicator(bot.content.isSearching, Modifier.align(Alignment.TopCenter))
        }
    }
    scheduling?.let { entry ->
        ScheduleDialog(
            now = now,
            onDismiss = { scheduling = null },
            onSchedule = { at ->
                scheduling = null
                onEvent(InlineBotEvent.Send(entry.queryId, entry.result.id, SendOptions(scheduledAt = at.epochSecond)))
            },
        )
    }
}

/**
 * The results in one [LazyVerticalGrid]: photos, GIFs, stickers and videos as square cells (at least
 * `sizes.sharedMediaCell`, or `sizes.panelSticker` where all are stickers, `sizes.mediaAlbumGap` apart, decoded at the
 * cells' real size), every other result a row across it, in the bot's order; the bot's button first. The next page
 * loads [LOAD_MORE_DISTANCE] items from the end ([InlineBotEvent.LoadMore]), with a loading indicator below the last
 * row, or its error with "Try again". A new query starts at the top.
 */
@Composable
private fun ResultsGrid(
    bot: InlineBotUiState,
    content: InlineResultsContent.Results,
    highlighted: Int,
    actions: InlineSendActions,
    onEvent: (InlineBotEvent) -> Unit,
) {
    val sizes = MaterialTheme.sizes
    val spacing = MaterialTheme.spacing
    val gridState = rememberLazyGridState()
    val first = if (content.button != null) 1 else 0
    LaunchedEffect(bot.queryKey) { gridState.scrollToItem(0) }
    LaunchedEffect(highlighted) { if (highlighted >= 0) gridState.animateScrollToItem(highlighted + first) }
    if (content.page == InlinePage.More) LoadMoreEffect(gridState) { onEvent(InlineBotEvent.LoadMore) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = sizes.mediaAlbumGap
        val cellMin = if (content.cells == InlineCellSize.Stickers) sizes.panelSticker else sizes.sharedMediaCell
        val inner = maxWidth - spacing.small * 2
        val columns = (inner / cellMin).toInt().coerceAtLeast(1)
        val cellSize = (inner - gap * (columns - 1)) / columns
        val cellPx = with(LocalDensity.current) { cellSize.roundToPx() }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(spacing.small),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            content.button?.let { button ->
                item(key = KEY_BUTTON, span = { GridItemSpan(maxLineSpan) }, contentType = KEY_BUTTON) {
                    ResultsButton(button) { onEvent(InlineBotEvent.OpenButton) }
                }
            }
            itemsIndexed(
                items = content.results,
                key = { _, entry -> entry.key },
                span = { _, entry -> if (entry.isCell) GridItemSpan(1) else GridItemSpan(maxLineSpan) },
                contentType = { _, entry -> entry.result::class },
            ) { index, entry ->
                if (entry.isCell) {
                    InlineResultCell(entry, cellSize, cellPx, index == highlighted, actions)
                } else {
                    InlineResultRow(entry, index == highlighted, actions)
                }
            }
            pageFooter(content.page, onEvent)
        }
    }
}

/** What follows the last result: a loading indicator while the next page comes, or its error with "Try again". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyGridScope.pageFooter(
    page: InlinePage,
    onEvent: (InlineBotEvent) -> Unit,
) {
    when (page) {
        InlinePage.Loading -> {
            item(key = KEY_FOOTER, span = { GridItemSpan(maxLineSpan) }, contentType = KEY_FOOTER) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
        }

        is InlinePage.Failed -> {
            item(key = KEY_FOOTER, span = { GridItemSpan(maxLineSpan) }, contentType = KEY_FOOTER) {
                ErrorSegment(errorText(page.error), canRetry = true) { onEvent(InlineBotEvent.LoadMore) }
            }
        }

        InlinePage.Done, InlinePage.More -> {}
    }
}

/** Calls [onLoadMore] once the grid shows one of its last [LOAD_MORE_DISTANCE] items. */
@Composable
private fun LoadMoreEffect(
    gridState: LazyGridState,
    onLoadMore: () -> Unit,
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - LOAD_MORE_DISTANCE
        }.distinctUntilChanged()
            .filter { it }
            .collect { currentOnLoadMore() }
    }
}

/** The pane's title: "@gif, 24 results", or the bot alone while there are none. */
@Composable
private fun panelTitle(bot: InlineBotUiState): String {
    val count = (bot.content as? InlineResultsContent.Results)?.results?.size ?: 0
    val name = "@${bot.username}"
    return if (count > 0) pluralStringResource(R.plurals.inline_results_title, count, name, count) else name
}

/** The tallest the panel gets: [WINDOW_FRACTION] of the window, so the conversation stays in view. */
@Composable
private fun maxPanelHeight(): Dp {
    val windowHeight =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    return windowHeight * WINDOW_FRACTION
}

/** Whether a newer query is on its way while these results show. */
internal val InlineResultsContent.isSearching: Boolean
    get() =
        when (this) {
            is InlineResultsContent.Results -> isSearching
            is InlineResultsContent.Empty -> isSearching
            else -> false
        }

/** The panel takes at most this part of the window's height. */
private const val WINDOW_FRACTION = 0.4f

/** How many items before the end the next page starts loading. */
private const val LOAD_MORE_DISTANCE = 6

private const val KEY_BUTTON = "button"
private const val KEY_FOOTER = "footer"

/** The panel, for UI tests. */
internal const val INLINE_RESULTS_TEST_TAG = "inline_results"
