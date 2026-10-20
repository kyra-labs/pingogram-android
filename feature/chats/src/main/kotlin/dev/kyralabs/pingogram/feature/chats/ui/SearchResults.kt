package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/**
 * The suggestions or the results of [content] in one `LazyColumn` (`docs/DESIGN_SYSTEM.md` §6, §3.3), every entry keyed
 * and typed, at most [contentMaxWidth][dev.kyralabs.pingogram.core.ui.theme.PingogramSizes.contentMaxWidth] wide, and
 * animating to its place and in and out on the motion scheme's springs as sections arrive.
 *
 * - **Suggestions**: "People", a row of the top people, then "Recent", the recent chats with a button to remove each
 *   and one to clear them all.
 * - **Results**: Chats, Contacts, Global search and Messages, each under a header (`titleSmallEmphasized` in
 *   `primary`, a heading for screen readers) with a loading indicator, an error with "Try again", or its rows; a
 *   section that found nothing is left out. Message rows show the query's words highlighted.
 * - **Paging**: while more messages can load, it reports the end coming within [LOAD_MORE_THRESHOLD] entries
 *   ([SearchEvent.LoadMoreMessages]), again whenever entries were added and the end is still near.
 *
 * Rows run no effects (`docs/PERFORMANCE.md` §4); their texts are mapped by the view model.
 */
@Composable
internal fun SearchResultsList(
    content: SearchContent,
    onEvent: (SearchEvent) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val style = RowStyle(MaterialTheme.sizes.contentMaxWidth, MaterialTheme.motionScheme)
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (content) {
            is SearchContent.Suggestions -> suggestions(content, onEvent, style)
            is SearchContent.Results -> results(content, onEvent, style)
        }
    }
    val canLoadMore = (content as? SearchContent.Results)?.messages?.canLoadMore == true
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            // The entry count, so that a page that leaves the end near asks again.
            layout.totalItemsCount.takeIf { it > 0 && lastVisible >= it - 1 - LOAD_MORE_THRESHOLD }
        }.filterNotNull()
            .distinctUntilChanged()
            .collect { currentOnEvent(SearchEvent.LoadMoreMessages) }
    }
}

/** How rows are laid out and animated: at most [maxWidth] wide, on the [motionScheme]'s springs. */
private class RowStyle(
    val maxWidth: Dp,
    val motionScheme: MotionScheme,
)

private fun LazyListScope.suggestions(
    content: SearchContent.Suggestions,
    onEvent: (SearchEvent) -> Unit,
    style: RowStyle,
) {
    if (content.topPeople.isNotEmpty()) {
        item(key = PEOPLE_HEADER, contentType = HEADER) {
            SectionHeader(stringResource(R.string.search_section_people), Modifier.row(this, style))
        }
        item(key = PEOPLE, contentType = PEOPLE) {
            TopPeople(content.topPeople, { onEvent(SearchEvent.OpenChat(it)) }, Modifier.row(this, style))
        }
    }
    if (content.recents.isNotEmpty()) {
        item(key = RECENT_HEADER, contentType = HEADER) {
            SectionHeader(stringResource(R.string.search_section_recent), Modifier.row(this, style)) {
                TextButton(onClick = { onEvent(SearchEvent.ClearRecents) }) {
                    Text(
                        text = stringResource(R.string.search_clear_recents),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }
            }
        }
        items(content.recents, key = { "$RECENT:${it.id}" }, contentType = { CHAT_ROW }) { item ->
            SearchChatRow(
                item = item,
                onClick = { onEvent(SearchEvent.OpenChat(item.id)) },
                modifier = Modifier.row(this, style),
                onRemove = { onEvent(SearchEvent.RemoveRecent(item.id)) },
            )
        }
    }
}

private fun LazyListScope.results(
    content: SearchContent.Results,
    onEvent: (SearchEvent) -> Unit,
    style: RowStyle,
) {
    chatSection(CHATS, R.string.search_section_chats, content.chats, onEvent, style)
    chatSection(CONTACTS, R.string.search_section_contacts, content.contacts, onEvent, style)
    chatSection(GLOBAL, R.string.search_section_global, content.global, onEvent, style)
    messageSection(content.messages, content.highlight, onEvent, style)
}

/** A section of chats under its header; nothing if it found none. */
private fun LazyListScope.chatSection(
    key: String,
    title: Int,
    section: SearchSection<SearchChatItem>,
    onEvent: (SearchEvent) -> Unit,
    style: RowStyle,
) {
    if (section is SearchSection.Items && section.items.isEmpty()) return
    item(key = "$key:$HEADER", contentType = HEADER) { SectionHeader(stringResource(title), Modifier.row(this, style)) }
    when (section) {
        SearchSection.Loading -> {
            item(key = "$key:$LOADING", contentType = LOADING) { SectionLoading(Modifier.row(this, style)) }
        }

        is SearchSection.Failed -> {
            item(key = "$key:$ERROR", contentType = ERROR) {
                SectionError(section.error, { onEvent(SearchEvent.Retry) }, Modifier.row(this, style))
            }
        }

        is SearchSection.Items -> {
            items(section.items, key = { "$key:${it.id}" }, contentType = { CHAT_ROW }) { item ->
                SearchChatRow(item, { onEvent(SearchEvent.OpenChat(item.id)) }, Modifier.row(this, style))
            }
        }
    }
}

/** The messages under their header, then the next page loading or failing; nothing if none was found. */
private fun LazyListScope.messageSection(
    section: MessageSection,
    highlight: MatchHighlight,
    onEvent: (SearchEvent) -> Unit,
    style: RowStyle,
) {
    val first = section.first
    if (first is SearchSection.Items && first.items.isEmpty()) return
    item(key = "$MESSAGES:$HEADER", contentType = HEADER) {
        SectionHeader(stringResource(R.string.search_section_messages), Modifier.row(this, style))
    }
    when (first) {
        SearchSection.Loading -> {
            item(key = "$MESSAGES:$LOADING", contentType = LOADING) { SectionLoading(Modifier.row(this, style)) }
        }

        is SearchSection.Failed -> {
            item(key = "$MESSAGES:$ERROR", contentType = ERROR) {
                SectionError(first.error, { onEvent(SearchEvent.Retry) }, Modifier.row(this, style))
            }
        }

        is SearchSection.Items -> {
            items(first.items, key = { "$MESSAGES:${it.chatId}:${it.messageId}" }, contentType = { MESSAGE_ROW }) {
                SearchMessageRow(
                    item = it,
                    highlight = highlight,
                    onClick = { onEvent(SearchEvent.OpenMessage(it.chatId, it.messageId)) },
                    modifier = Modifier.row(this, style),
                )
            }
        }
    }
    val moreError = section.moreError
    when {
        moreError != null -> {
            item(key = "$MESSAGES:$MORE_ERROR", contentType = ERROR) {
                SectionError(moreError, { onEvent(SearchEvent.Retry) }, Modifier.row(this, style))
            }
        }

        section.isLoadingMore -> {
            item(key = "$MESSAGES:$MORE", contentType = LOADING) { SectionLoading(Modifier.row(this, style)) }
        }
    }
}

/** A row of [scope] at most [RowStyle.maxWidth] wide, animating as the list changes on the motion scheme's springs. */
private fun Modifier.row(
    scope: LazyItemScope,
    style: RowStyle,
): Modifier =
    with(scope) {
        this@row
            .widthIn(max = style.maxWidth)
            .fillMaxWidth()
            .animateItem(
                fadeInSpec = style.motionScheme.defaultEffectsSpec(),
                placementSpec = style.motionScheme.defaultSpatialSpec(),
                fadeOutSpec = style.motionScheme.fastEffectsSpec(),
            )
    }

/** A section's name: a heading in `titleSmallEmphasized` and `primary` (`docs/DESIGN_SYSTEM.md` §3.3); [action]. */
@Composable
internal fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = modifier.padding(start = spacing.large, end = spacing.small, top = spacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(vertical = spacing.small)
                    .semantics { heading() },
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmallEmphasized,
        )
        action?.invoke()
    }
}

/** A section still searching: the loading indicator, named for screen readers. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionLoading(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.search_searching)
    Box(
        modifier = modifier.padding(vertical = MaterialTheme.spacing.small),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(Modifier.semantics { contentDescription = description })
    }
}

/** A section, or a page of messages, that failed: the error's text and "Try again" ([onRetry]). */
@Composable
internal fun SectionError(
    error: DomainError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = modifier.padding(horizontal = spacing.large, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = errorText(error),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        FilledTonalButton(onClick = onRetry) {
            Text(text = stringResource(R.string.search_retry), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/** The color behind rows: the search bar's container, which sets off avatars' online dots. */
internal val rowContainerColor: Color
    @Composable get() = SearchBarDefaults.colors().containerColor

private const val LOAD_MORE_THRESHOLD = 5

// Keys and content types of the list's entries; keys of rows add the section and the chat or message.
private const val HEADER = "header"
private const val LOADING = "loading"
private const val ERROR = "error"
private const val MORE = "more"
private const val MORE_ERROR = "moreError"
private const val CHAT_ROW = "chat"
private const val MESSAGE_ROW = "message"
private const val PEOPLE = "people"
private const val PEOPLE_HEADER = "people:header"
private const val RECENT = "recent"
private const val RECENT_HEADER = "recent:header"
private const val CHATS = "chats"
private const val CONTACTS = "contacts"
private const val GLOBAL = "global"
private const val MESSAGES = "messages"
