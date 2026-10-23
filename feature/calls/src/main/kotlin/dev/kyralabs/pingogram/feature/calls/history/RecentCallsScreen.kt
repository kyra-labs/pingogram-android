package dev.kyralabs.pingogram.feature.calls.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.calls.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Recent calls (`docs/DESIGN_SYSTEM.md` §6, "Recent calls"), pushed above the screen it opens from: a small
 * [TopAppBar] with back on `surfaceContainer`, the content on a [ContentSheet]: "All" and "Missed" as [FilterChip]s,
 * then the calls by day under `titleSmallEmphasized` headers ([RecentCallRow]). A tap calls back the way the call went;
 * a long press offers to delete the call from the history. The next page loads [LOAD_MORE_DISTANCE] rows from the
 * end. Loading is a [DelayedLoadingIndicator]; no calls and a failure are [EmptyState]s.
 */
@Composable
internal fun RecentCallsScreen(
    state: RecentCallsUiState,
    onEvent: (RecentCallsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = { RecentCallsTopBar(onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            RecentCallsBody(state, onEvent, listState, contentPadding)
        }
    }
}

/** The small top bar: back and "Recent calls", its title at the window margin, as the archive's. */
@Composable
private fun RecentCallsTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.recent_calls_title),
                modifier = Modifier.semantics { heading() },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.recent_calls_back),
                )
            }
        },
        modifier = Modifier.padding(horizontal = windowMargin() - MaterialTheme.spacing.windowMarginCompact),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        expandedHeight = MaterialTheme.sizes.denseTopAppBar,
    )
}

/**
 * The filters above what the sheet shows for [state]. Its own composable, so the frames in which the `Scaffold`
 * measures its content anew stop here.
 */
@Composable
private fun RecentCallsBody(
    state: RecentCallsUiState,
    onEvent: (RecentCallsEvent) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Filters(state.filter, onSelect = { onEvent(RecentCallsEvent.SelectFilter(it)) })
        val bottom = PaddingValues(bottom = contentPadding.calculateBottomPadding())
        when (val content = state.content) {
            RecentCallsContent.Loading -> DelayedLoadingIndicator(Modifier.fillMaxSize().padding(bottom))
            RecentCallsContent.Empty -> NoCalls(state.filter, Modifier.padding(bottom))
            is RecentCallsContent.Error -> LoadFailed(content, onRetry = { onEvent(RecentCallsEvent.Retry) })
            is RecentCallsContent.Calls -> CallList(content, onEvent, listState, bottom)
        }
    }
}

/** "All" and "Missed" as [FilterChip]s, one choice for screen readers, at the window margin. */
@Composable
private fun Filters(
    filter: CallFilter,
    onSelect: (CallFilter) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = windowMargin())
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        CallFilter.entries.forEach { option ->
            FilterChip(
                selected = option == filter,
                onClick = { onSelect(option) },
                label = { Text(stringResource(option.label())) },
                modifier = Modifier.semantics { role = Role.RadioButton },
                shapes = FilterChipDefaults.shapes(),
            )
        }
    }
}

private fun CallFilter.label() =
    when (this) {
        CallFilter.All -> R.string.recent_calls_filter_all
        CallFilter.Missed -> R.string.recent_calls_filter_missed
    }

/** The calls by day; the next page loads near the end, with its indicator or its error below the last row. */
@Composable
private fun CallList(
    content: RecentCallsContent.Calls,
    onEvent: (RecentCallsEvent) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val event by rememberUpdatedState(onEvent)
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_DISTANCE
        }
    }
    LaunchedEffect(listState, content.reachedEnd) {
        if (!content.reachedEnd) {
            snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { event(RecentCallsEvent.LoadMore) }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        items(content.items, key = { it.key }, contentType = { it::class }) { item ->
            when (item) {
                is RecentCallsItem.Day -> {
                    DayHeader(item)
                }

                is RecentCallsItem.Call -> {
                    RecentCallRow(
                        call = item,
                        onClick = { onEvent(RecentCallsEvent.CallBack(item)) },
                        onDelete = { onEvent(RecentCallsEvent.Delete(item)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
        footer(content, onRetry = { onEvent(RecentCallsEvent.Retry) })
    }
}

/** The next page's indicator, or its error with "Try again". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.footer(
    content: RecentCallsContent.Calls,
    onRetry: () -> Unit,
) {
    when {
        content.pageError != null -> {
            item(key = FOOTER, contentType = FOOTER) { PageError(errorText(content.pageError), onRetry) }
        }

        content.loadingMore -> {
            item(key = FOOTER, contentType = FOOTER) {
                Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium), Alignment.Center) {
                    LoadingIndicator(Modifier.size(MaterialTheme.sizes.inlineProgressIndicator))
                }
            }
        }
    }
}

/** A failed next page: why, in `error` as a polite live region, and "Try again". */
@Composable
private fun PageError(
    message: String,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = windowMargin(), vertical = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.recent_calls_try_again)) }
    }
}

/** A day's header ("Today", "Yesterday", the date) in `titleSmallEmphasized` `primary`, a heading. */
@Composable
private fun DayHeader(day: RecentCallsItem.Day) {
    Text(
        text = day.label.resolve(),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = windowMargin(),
                    end = windowMargin(),
                    top = MaterialTheme.spacing.medium,
                    bottom = MaterialTheme.spacing.small,
                ).semantics { heading() },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmallEmphasized,
    )
}

/** How many rows from the end the next page starts loading. */
internal const val LOAD_MORE_DISTANCE = 5
private const val FOOTER = "footer"
