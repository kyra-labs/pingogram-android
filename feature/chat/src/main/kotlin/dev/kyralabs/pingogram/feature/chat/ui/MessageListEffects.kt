package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// What the message list reports and follows, read from its layout, outside the items (docs/PERFORMANCE.md §4).

/**
 * Reports whether [listState] shows its oldest entry, or one within [LOAD_MORE_THRESHOLD] of it
 * ([ChatEvent.NearOldestChanged]), and the same for its newest one ([ChatEvent.NearNewestChanged]): on every change,
 * read from the layout, and false for both once the list leaves the screen.
 */
@Composable
internal fun ReportNearEnds(
    listState: LazyListState,
    onEvent: (ChatEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState) {
        try {
            snapshotFlow {
                val layout = listState.layoutInfo
                val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 1 - LOAD_MORE_THRESHOLD
            }.distinctUntilChanged()
                .collect { currentOnEvent(ChatEvent.NearOldestChanged(it)) }
        } finally {
            // Leaving the screen, or the list.
            currentOnEvent(ChatEvent.NearOldestChanged(false))
        }
    }
    LaunchedEffect(listState) {
        try {
            snapshotFlow {
                val layout = listState.layoutInfo
                val firstVisible = layout.visibleItemsInfo.firstOrNull()?.index ?: Int.MAX_VALUE
                layout.totalItemsCount > 0 && firstVisible <= LOAD_MORE_THRESHOLD
            }.distinctUntilChanged()
                .collect { currentOnEvent(ChatEvent.NearNewestChanged(it)) }
        } finally {
            currentOnEvent(ChatEvent.NearNewestChanged(false))
        }
    }
}

/**
 * Reports the messages of the entries [listState] shows, wholly or in part ([ChatEvent.MessagesVisible]): on every
 * change of them, as the list scrolls or messages come and go, and none once the list leaves the screen. Read from the
 * layout, outside the items (`docs/PERFORMANCE.md` §4). A frame of a scroll only compares the keys of the entries at
 * both ends of the screen; the ids are gathered, by key, when those or the items change.
 */
@Composable
internal fun ReportVisibleMessages(
    items: ImmutableList<ChatItem>,
    listState: LazyListState,
    onEvent: (ChatEvent) -> Unit,
) {
    val currentItems by rememberUpdatedState(items)
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState) {
        try {
            val ends =
                snapshotFlow {
                    val visible = listState.layoutInfo.visibleItemsInfo
                    visible.firstOrNull()?.key to visible.lastOrNull()?.key
                }
            // By key, as the layout can be a frame behind the items: a new message shifts every index.
            val itemsByKey = snapshotFlow { currentItems }.map { items -> items.associateBy { it.key } }
            combine(ends, itemsByKey) { _, byKey ->
                listState.layoutInfo.visibleItemsInfo.flatMapTo(HashSet()) { byKey[it.key]?.messageIds.orEmpty() }
            }.distinctUntilChanged()
                .collect { currentOnEvent(ChatEvent.MessagesVisible(it)) }
        } finally {
            currentOnEvent(ChatEvent.MessagesVisible(emptySet()))
        }
    }
}

/** The messages an entry shows: a message, every item of an album; none for dates and the divider. */
private val ChatItem.messageIds: List<Long>
    get() =
        when (this) {
            is ChatItem.Bubble -> album?.messageIds ?: listOf(id)
            is ChatItem.Service -> listOf(id)
            is ChatItem.DateSeparator, is ChatItem.UnreadDivider -> emptyList()
        }

/**
 * Brings a new newest message into view when the list showed the newest one: a lazy list keeps its first visible
 * entry in place by key, which would leave a message added below it out of view. Elsewhere in the history the list
 * stays where the user scrolled to, and the jump button leads down. Only for a message that arrives at the chat's end
 * ([reachedEnd], before and after): a newer page coming in below stays below.
 */
@Composable
internal fun FollowNewestMessage(
    newestKey: Any?,
    reachedEnd: Boolean,
    listState: LazyListState,
) {
    var shownNewest by remember { mutableStateOf(newestKey to reachedEnd) }
    LaunchedEffect(newestKey, reachedEnd) {
        val (previousKey, previousReachedEnd) = shownNewest
        shownNewest = newestKey to reachedEnd
        if (newestKey == previousKey || !previousReachedEnd || !reachedEnd) return@LaunchedEffect
        // Entries may have come with it (a date separator), so the previous newest one is looked up by key.
        if (listState.layoutInfo.visibleItemsInfo.any { it.key == previousKey }) listState.requestScrollToItem(0)
    }
}

/** How close to the oldest or newest loaded message the list asks for more: about half a page of entries ahead. */
internal const val LOAD_MORE_THRESHOLD = 20
