package dev.kyralabs.pingogram.feature.chat.ui.directmessages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.GrowingVisibility
import dev.kyralabs.pingogram.core.ui.component.StateCrossfade
import dev.kyralabs.pingogram.core.ui.component.animateItem
import dev.kyralabs.pingogram.core.ui.component.rememberItemMotion
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatTopBar
import dev.kyralabs.pingogram.feature.chat.ui.forward.ChatSearchField
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A channel's direct messages as its administrators see them (the owner's board 7b, `docs/DESIGN_SYSTEM.md` §6
 * "Channel direct messages"): the conversation's top bar with the channel ([ChatTopBar]) on `surfaceContainer`, then on
 * a rounded `surface` sheet ([ContentSheet]) the search bar's field (the senders' names), the filters All, Unread and
 * Paid as `FilterChip`s with their counts, and one row per person who wrote ([DirectMessageRowItem]). A tap opens the
 * person's conversation through [onOpenTopic]; [onBack] is the top bar's back button, none beside the chat list.
 *
 * - **States**: a loading indicator once loading takes a moment, an empty state (nobody wrote, or nobody under the
 *   filter), an error with "Try again" ([DirectMessagesEvent.Retry]).
 * - **Pages**: the list asks for more as its last row comes near ([DirectMessagesEvent.LoadMore]), a loading
 *   indicator below the rows meanwhile.
 */
@Suppress("LongParameterList") // The state, its events, where a row opens, back and the hoisted states.
@Composable
internal fun DirectMessagesScreen(
    state: DirectMessagesUiState,
    onEvent: (DirectMessagesEvent) -> Unit,
    onOpenTopic: (topicId: Long) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    query: TextFieldState = rememberTextFieldState(),
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(query) {
        snapshotFlow { query.text.toString() }.distinctUntilChanged().collect {
            currentOnEvent(DirectMessagesEvent.QueryChanged(it))
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = { DirectTopBar(state.header, onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
                ChatSearchField(query, placeholder = stringResource(R.string.direct_search))
                FilterChips(state, onEvent)
                DirectBody(
                    state.content,
                    onEvent,
                    onOpenTopic,
                    listState,
                    PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                )
            }
        }
    }
}

/** All, Unread and Paid as `FilterChip`s, one radio group for screen readers, "All · 12" and "Unread · 4". */
@Composable
private fun FilterChips(
    state: DirectMessagesUiState,
    onEvent: (DirectMessagesEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    LazyRow(
        modifier = Modifier.fillMaxWidth().semantics { selectableGroup() },
        contentPadding = PaddingValues(horizontal = spacing.large),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        items(DirectFilter.entries, key = { it.name }) { filter ->
            val count =
                when (filter) {
                    DirectFilter.All -> state.total
                    DirectFilter.Unread -> state.unread
                    DirectFilter.Paid -> null
                }
            val label = stringResource(filter.label)
            FilterChip(
                selected = state.filter == filter,
                onClick = { onEvent(DirectMessagesEvent.FilterChanged(filter)) },
                label = {
                    Text(
                        if (count != null &&
                            count > 0
                        ) {
                            stringResource(R.string.direct_filter_count, label, count)
                        } else {
                            label
                        },
                    )
                },
                modifier = Modifier.semantics { role = Role.RadioButton },
                shapes = FilterChipDefaults.shapes(),
            )
        }
    }
}

/** What the sheet shows for [content] below the field and the chips. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DirectBody(
    content: DirectContent,
    onEvent: (DirectMessagesEvent) -> Unit,
    onOpenTopic: (topicId: Long) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val bodyModifier = Modifier.fillMaxSize().padding(contentPadding)
    // Loading, empty, a failure and the rows crossfade in place; new rows within the list do not.
    StateCrossfade(targetState = content, contentKey = { it::class }, label = "directBody") { shown ->
        DirectBodyContent(shown, onEvent, onOpenTopic, listState, contentPadding, bodyModifier)
    }
}

@Suppress("LongParameterList") // DirectBody's arguments, and the modifier of its states.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DirectBodyContent(
    content: DirectContent,
    onEvent: (DirectMessagesEvent) -> Unit,
    onOpenTopic: (topicId: Long) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    bodyModifier: Modifier,
) {
    when (content) {
        DirectContent.Loading -> {
            Box(bodyModifier, contentAlignment = Alignment.Center) { DelayedLoadingIndicator() }
        }

        is DirectContent.Empty -> {
            EmptyState(
                illustration = MaterialShapes.Cookie9Sided,
                icon = UiR.drawable.ic_mark_chat_unread,
                title =
                    stringResource(
                        if (content.isFiltered) R.string.direct_empty_filtered_title else R.string.direct_empty_title,
                    ),
                body =
                    stringResource(
                        if (content.isFiltered) R.string.direct_empty_filtered_body else R.string.direct_empty_body,
                    ),
                modifier = bodyModifier,
            )
        }

        is DirectContent.Failed -> {
            EmptyState(
                illustration = MaterialShapes.Cookie4Sided,
                icon = UiR.drawable.ic_error,
                title = stringResource(R.string.direct_error_title),
                body = errorText(content.error),
                modifier = bodyModifier,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                iconColor = MaterialTheme.colorScheme.onErrorContainer,
                action = {
                    Button(onClick = { onEvent(DirectMessagesEvent.Retry) }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.chat_retry), style = MaterialTheme.typography.labelLargeEmphasized)
                    }
                },
            )
        }

        is DirectContent.Rows -> {
            DirectList(content, onEvent, onOpenTopic, listState, contentPadding)
        }
    }
}

/**
 * The top bar of the channel the direct messages are of ([header], null until it is known), growing in once known
 * instead of pushing the page down at once.
 */
@Composable
private fun DirectTopBar(
    header: ChatHeader?,
    onBack: (() -> Unit)?,
) {
    // Kept, not state, so keeping it recomposes nothing.
    val last = remember { arrayOfNulls<ChatHeader>(1) }
    if (header != null) last[0] = header
    GrowingVisibility(visible = header != null) { last[0]?.let { ChatTopBar(header = it, onBack = onBack) } }
}

/** The rows, keyed by topic, then a loading indicator while the next page loads; asks for it near the end. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DirectList(
    content: DirectContent.Rows,
    onEvent: (DirectMessagesEvent) -> Unit,
    onOpenTopic: (topicId: Long) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            (layout.visibleItemsInfo.lastOrNull()?.index ?: 0) >= layout.totalItemsCount - LOAD_MORE_THRESHOLD
        }.distinctUntilChanged()
            .filter { it }
            .collect { currentOnEvent(DirectMessagesEvent.LoadMore) }
    }
    val motion = rememberItemMotion()
    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        items(content.rows, key = { it.topicId }, contentType = { CONTENT_TYPE_ROW }) { row ->
            DirectMessageRowItem(
                row = row,
                onClick = { onOpenTopic(row.topicId) },
                onMarkUnread = { onEvent(DirectMessagesEvent.MarkUnread(row.topicId, it)) },
                modifier = Modifier.animateItem(this, motion),
            )
        }
        if (content.isLoadingMore) {
            item(key = KEY_LOADING, contentType = KEY_LOADING) {
                Box(
                    Modifier.animateItem(this, motion).fillMaxWidth().padding(MaterialTheme.spacing.large),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 5
private const val CONTENT_TYPE_ROW = "row"
private const val KEY_LOADING = "loading"
