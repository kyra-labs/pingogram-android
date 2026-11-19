package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.animateItem
import dev.kyralabs.pingogram.core.ui.component.rememberItemMotion
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.SearchMessageRow
import dev.kyralabs.pingogram.feature.chats.ui.SectionErrorSegment
import dev.kyralabs.pingogram.feature.chats.ui.preview.FeedSearchPreviewStates
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What the feed's search follows while [searching]: the [query] typed, the feed's [chosen] channels, and back, which
 * closes the search ([onClose]) before it leaves the feed.
 */
@Composable
internal fun FeedSearchEffects(
    searching: Boolean,
    query: TextFieldState,
    chosen: Set<Long>?,
    onEvent: (FeedSearchEvent) -> Unit,
    onClose: () -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    BackHandler(enabled = searching, onBack = onClose)
    LaunchedEffect(query) {
        snapshotFlow { query.text.toString() }.collect { currentOnEvent(FeedSearchEvent.QueryChanged(it)) }
    }
    LaunchedEffect(chosen) { currentOnEvent(FeedSearchEvent.ChannelsChosen(chosen)) }
}

/**
 * The feed's search field in place of its top bar (`docs/DESIGN_SYSTEM.md` §6 "Channel feed"): the search bar's input
 * field, as global search's, focused as it opens, with back closing the search ([onClose]) and clear emptying it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedSearchBar(
    textFieldState: TextFieldState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchBarState = rememberSearchBarState(initialValue = SearchBarValue.Expanded)
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    SearchBarDefaults.InputField(
        textFieldState = textFieldState,
        searchBarState = searchBarState,
        onSearch = { keyboard?.hide() },
        modifier =
            modifier
                .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
                .focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.feed_search_placeholder)) },
        leadingIcon = {
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(R.string.feed_search_close))
            }
        },
        trailingIcon =
            if (textFieldState.text.isNotEmpty()) {
                {
                    IconButton(onClick = { textFieldState.clearText() }, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(UiR.drawable.ic_close), stringResource(R.string.search_clear))
                    }
                }
            } else {
                null
            },
    )
}

/**
 * What the feed's search found (`REQUIREMENTS.md` FR-X5): before a query, what it searches; the posts as global
 * search's message rows ([SearchMessageRow], one segmented group), a tap opening the post in its channel ([onOpen]),
 * the next page read as the end nears; a failed page's error with "Try again"; and, for no posts, global search's
 * "No results".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FeedSearchResults(
    state: FeedSearchUiState,
    onEvent: (FeedSearchEvent) -> Unit,
    onOpen: (chatId: Long, messageId: Long) -> Unit,
    contentPadding: PaddingValues,
) {
    val body = Modifier.fillMaxSize().padding(contentPadding)
    when {
        state.query.isEmpty() -> {
            EmptyState(
                illustration = MaterialShapes.Cookie9Sided,
                icon = UiR.drawable.ic_search,
                title = stringResource(R.string.feed_search_hint_title),
                body = stringResource(R.string.feed_search_hint_body),
                modifier = body,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        state.isEmpty -> {
            EmptyState(
                illustration = MaterialShapes.Sunny,
                icon = UiR.drawable.ic_search,
                title = stringResource(R.string.search_no_results_title, state.query.bidiIsolated()).bidiIsolated(),
                body = stringResource(R.string.search_no_results_body),
                modifier = body,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }

        else -> {
            FoundPosts(state, onEvent, onOpen, contentPadding)
        }
    }
}

/** The rows, then the next page's loading indicator or error; the next page is asked for near the end. */
@Composable
private fun FoundPosts(
    state: FeedSearchUiState,
    onEvent: (FeedSearchEvent) -> Unit,
    onOpen: (chatId: Long, messageId: Long) -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    val motion = rememberItemMotion()
    val row =
        Modifier
            .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.medium)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(state.results, key = { _, item -> "${item.chatId}:${item.messageId}" }) { index, item ->
            SearchMessageRow(
                item = item,
                highlight = state.highlight,
                shapes = ListItemDefaults.segmentedShapes(index, state.results.size),
                onClick = { onOpen(item.chatId, item.messageId) },
                modifier = row.animateItem(this, motion),
            )
        }
        val error = state.error
        if (error != null) {
            item(key = FOOTER) { SectionErrorSegment(error, onRetry = { onEvent(FeedSearchEvent.Retry) }, row) }
        } else if (state.isLoading || state.canLoadMore) {
            item(key = FOOTER) {
                Box(row.padding(vertical = MaterialTheme.spacing.medium), contentAlignment = Alignment.Center) {
                    DelayedLoadingIndicator()
                }
            }
        }
    }
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState, state.canLoadMore) {
        if (!state.canLoadMore) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            // The entry count, so that a page that leaves the end near asks again.
            layout.totalItemsCount.takeIf { lastVisible >= it - 1 - LOAD_MORE_THRESHOLD }
        }.filter { it != null }
            .distinctUntilChanged()
            .collect { currentOnEvent(FeedSearchEvent.LoadMore) }
    }
}

private const val FOOTER = "footer"
private const val LOAD_MORE_THRESHOLD = 5

@PingogramPreviews
@Composable
private fun FeedSearchResultsPreview(
    @PreviewParameter(FeedSearchPreviewStates::class) state: FeedSearchUiState,
) {
    PingogramPreview { FeedSearchResults(state, onEvent = {}, onOpen = { _, _ -> }, PaddingValues()) }
}
