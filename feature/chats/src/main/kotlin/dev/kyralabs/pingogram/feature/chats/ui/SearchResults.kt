package dev.kyralabs.pingogram.feature.chats.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The suggestions or the results of [content] in one `LazyColumn` (`docs/DESIGN_SYSTEM.md` §6 "Global search", §3.3),
 * every entry keyed and typed, at most [contentMaxWidth][dev.kyralabs.pingogram.core.ui.theme.PingogramSizes.contentMaxWidth]
 * wide, and animating to its place and in and out on the motion scheme's springs as sections arrive.
 *
 * - **Sections** are Material 3 Expressive segmented lists, as settings pages show theirs: each a group of
 *   [SegmentedListItem][androidx.compose.material3.SegmentedListItem]s inset by the window margin, the rows
 *   `ListItemDefaults.SegmentedGap` apart with large outer corners at the ends of the group
 *   (`ListItemDefaults.segmentedShapes`), under a header (`titleSmallEmphasized` in `primary`, a heading for screen
 *   readers) that keeps the groups apart.
 * - **Suggestions**: "People", a row of the top people, then "Recent", the recent chats with a button to remove each
 *   and one to clear them all.
 * - **Results**: Chats, Contacts, Global search and Messages (or Photos and videos, or Links, under those filters),
 *   each with its rows or its error ([SectionErrorSegment]); a section still searching or that found nothing is left
 *   out, as the progress indicator under the search field shows the search (`SearchScreen`). Message rows show the
 *   query's words highlighted.
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
    val spacing = MaterialTheme.spacing
    val style = RowStyle(MaterialTheme.sizes.contentMaxWidth, spacing.windowMarginCompact, MaterialTheme.motionScheme)
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + spacing.large,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        // The gap between segmented rows; headers keep the groups apart with their own padding besides.
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
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

/** How rows are laid out and animated: at most [maxWidth] wide, inset by [margin], on the [motionScheme]'s springs. */
private class RowStyle(
    val maxWidth: Dp,
    val margin: Dp,
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
            // Edge to edge, so the people scroll out under the window's sides.
            TopPeople(
                content.topPeople,
                { onEvent(SearchEvent.OpenChat(it)) },
                Modifier.row(this, style, inset = false),
            )
        }
    }
    if (content.recents.isNotEmpty()) {
        item(key = RECENT_HEADER, contentType = HEADER) {
            SectionHeader(stringResource(R.string.search_section_recent), Modifier.row(this, style)) {
                TextButton(onClick = { onEvent(SearchEvent.ClearRecents) }, shapes = ButtonDefaults.shapes()) {
                    Text(text = stringResource(R.string.search_clear_recents))
                }
            }
        }
        val count = content.recents.size
        itemsIndexed(
            items = content.recents,
            key = { _, item -> "$RECENT:${item.id}" },
            contentType = { _, _ -> CHAT_ROW },
        ) { index, item ->
            SearchChatSegment(
                item = item,
                shapes = ListItemDefaults.segmentedShapes(index, count),
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
    val messagesTitle =
        when (content.filter) {
            SearchFilter.Media -> R.string.search_section_media
            SearchFilter.Links -> R.string.search_section_links
            else -> R.string.search_section_messages
        }
    messageSection(messagesTitle, content.messages, content.highlight, onEvent, style)
}

/** A group of chats under its header; nothing while it searches or if it found none. */
private fun LazyListScope.chatSection(
    key: String,
    @StringRes title: Int,
    section: SearchSection<SearchChatItem>,
    onEvent: (SearchEvent) -> Unit,
    style: RowStyle,
) {
    if (section is SearchSection.Loading || (section is SearchSection.Items && section.items.isEmpty())) return
    item(key = "$key:$HEADER", contentType = HEADER) { SectionHeader(stringResource(title), Modifier.row(this, style)) }
    when (section) {
        SearchSection.Loading -> {}

        is SearchSection.Failed -> {
            item(key = "$key:$ERROR", contentType = ERROR) {
                SectionErrorSegment(section.error, { onEvent(SearchEvent.Retry) }, Modifier.row(this, style))
            }
        }

        is SearchSection.Items -> {
            val count = section.items.size
            itemsIndexed(
                items = section.items,
                key = { _, item -> "$key:${item.id}" },
                contentType = { _, _ -> CHAT_ROW },
            ) { index, item ->
                SearchChatSegment(
                    item = item,
                    shapes = ListItemDefaults.segmentedShapes(index, count),
                    onClick = { onEvent(SearchEvent.OpenChat(item.id)) },
                    modifier = Modifier.row(this, style),
                )
            }
        }
    }
}

/**
 * The messages as one group under their header [title], ending with the next page's error if it failed; nothing while
 * the first page loads or if none was found.
 */
private fun LazyListScope.messageSection(
    @StringRes title: Int,
    section: MessageSection,
    highlight: MatchHighlight,
    onEvent: (SearchEvent) -> Unit,
    style: RowStyle,
) {
    val first = section.first
    if (first is SearchSection.Loading || (first is SearchSection.Items && first.items.isEmpty())) return
    item(key = "$MESSAGES:$HEADER", contentType = HEADER) {
        SectionHeader(stringResource(title), Modifier.row(this, style))
    }
    if (first is SearchSection.Failed) {
        item(key = "$MESSAGES:$ERROR", contentType = ERROR) {
            SectionErrorSegment(first.error, { onEvent(SearchEvent.Retry) }, Modifier.row(this, style))
        }
        return
    }
    val items = (first as SearchSection.Items).items
    val moreError = section.moreError
    val count = items.size + if (moreError != null) 1 else 0
    itemsIndexed(
        items = items,
        key = { _, item -> "$MESSAGES:${item.chatId}:${item.messageId}" },
        contentType = { _, _ -> MESSAGE_ROW },
    ) { index, item ->
        SearchMessageRow(
            item = item,
            highlight = highlight,
            shapes = ListItemDefaults.segmentedShapes(index, count),
            onClick = { onEvent(SearchEvent.OpenMessage(item.chatId, item.messageId)) },
            modifier = Modifier.row(this, style),
        )
    }
    if (moreError != null) {
        item(key = "$MESSAGES:$MORE_ERROR", contentType = ERROR) {
            SectionErrorSegment(
                error = moreError,
                onRetry = { onEvent(SearchEvent.Retry) },
                modifier = Modifier.row(this, style),
                shapes = ListItemDefaults.segmentedShapes(count - 1, count),
            )
        }
    }
}

/**
 * A row of [scope] at most [RowStyle.maxWidth] wide, inset by [RowStyle.margin] unless not [inset], animating as the
 * list changes on the motion scheme's springs.
 */
private fun Modifier.row(
    scope: LazyItemScope,
    style: RowStyle,
    inset: Boolean = true,
): Modifier =
    with(scope) {
        this@row
            .animateItem(
                fadeInSpec = style.motionScheme.defaultEffectsSpec(),
                placementSpec = style.motionScheme.defaultSpatialSpec(),
                fadeOutSpec = style.motionScheme.fastEffectsSpec(),
            ).widthIn(max = style.maxWidth)
            .fillMaxWidth()
            .padding(horizontal = if (inset) style.margin else 0.dp)
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

/**
 * A section of search, or its next page of messages, that failed (`docs/DESIGN_SYSTEM.md` §6, "Global search"): a
 * segment of its group ([shapes], alone by default) in `errorContainer`, with the error icon, the error's text
 * (announced politely to screen readers) and "Try again" ([onRetry]) in `onErrorContainer`.
 */
@Composable
internal fun SectionErrorSegment(
    error: DomainError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    shapes: ListItemShapes = ListItemDefaults.segmentedShapes(0, 1),
) {
    val spacing = MaterialTheme.spacing
    val contentColor = MaterialTheme.colorScheme.onErrorContainer
    Row(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.errorContainer, shapes.shape)
                .padding(start = spacing.large, end = spacing.small, top = spacing.small, bottom = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(UiR.drawable.ic_error), contentDescription = null, tint = contentColor)
        Text(
            text = errorText(error),
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(
            onClick = onRetry,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
        ) {
            Text(text = stringResource(R.string.search_retry))
        }
    }
}

/**
 * The color behind search's results: the search bar's container, which sets off the people row's online dots and the
 * segmented rows' containers.
 */
internal val rowContainerColor: Color
    @Composable get() = SearchBarDefaults.colors().containerColor

private const val LOAD_MORE_THRESHOLD = 5

// Keys and content types of the list's entries; keys of rows add the section and the chat or message.
private const val HEADER = "header"
private const val ERROR = "error"
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
