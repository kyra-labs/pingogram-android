package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.interaction.DragInteraction
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
import kotlinx.coroutines.flow.filterIsInstance
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

/**
 * Reports each time the user starts dragging [listState] ([PinnedEvent.ListDragged]), so the pinned messages bar
 * follows the screen again after a tap chose its message. The list's own jumps (to a reply, to a pinned message) are
 * no drags, so they leave that choice.
 */
@Composable
internal fun ReportListDrags(
    listState: LazyListState,
    onEvent: (ChatEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState) {
        listState.interactionSource.interactions
            .filterIsInstance<DragInteraction.Start>()
            .collect { currentOnEvent(PinnedEvent.ListDragged) }
    }
}

/**
 * The messages an entry shows: a message, every item of an album; none for dates and the divider, nor for a sponsored
 * message, which [ReportSponsoredViews] reports only once it is wholly on screen.
 */
private val ChatItem.messageIds: List<Long>
    get() =
        when (this) {
            is ChatItem.Bubble -> album?.messageIds ?: listOf(id)
            is ChatItem.Service -> listOf(id)
            is ChatItem.DateSeparator, is ChatItem.UnreadDivider, is ChatItem.Sponsored -> emptyList()
        }

/**
 * Reports each sponsored card once it lies wholly inside the list's viewport within its content padding, clear of the
 * top bar and the composer ([SponsoredEvent.Viewed]): TDLib counts a sponsored message as viewed only once its whole
 * text is on screen. A card taller than the viewport (a large font in landscape) counts once it fills it. Read from
 * the layout, outside the items (`docs/PERFORMANCE.md` §4); while no card is among the items, a frame only checks that.
 */
@Composable
internal fun ReportSponsoredViews(
    items: ImmutableList<ChatItem>,
    listState: LazyListState,
    onEvent: (ChatEvent) -> Unit,
) {
    val currentItems by rememberUpdatedState(items)
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState) {
        snapshotFlow {
            val cards = currentItems.filterIsInstance<ChatItem.Sponsored>()
            if (cards.isEmpty()) return@snapshotFlow emptySet()
            val layout = listState.layoutInfo
            val viewport = layout.viewport
            val byKey = cards.associateBy { it.key }
            layout.visibleItemsInfo.mapNotNullTo(HashSet()) { info ->
                val card = byKey[info.key] ?: return@mapNotNullTo null
                val end = info.offset + info.size
                val whole = info.offset >= 0 && end <= viewport
                val fills = info.size > viewport && info.offset <= 0 && end >= viewport
                card.card.id.takeIf { whole || fills }
            }
        }.distinctUntilChanged()
            .collect { ids -> ids.forEach { currentOnEvent(SponsoredEvent.Viewed(it)) } }
    }
}

/**
 * Brings a new newest entry into view when the list showed the newest one: a lazy list keeps its first visible
 * entry in place by key, which would leave a message added below it out of view. Elsewhere in the history the list
 * stays where the user scrolled to, and the jump button leads down. Only for what arrives at the chat's end
 * ([reachedEnd], before and after): a newer page coming in below stays below.
 *
 * [newestKey] is the first entry's, [newestMessageKey] the newest message's ([newestMessageKey]): they differ while a
 * sponsored message is pinned below every message, where a new message arrives above it. The list follows either: a
 * new message, and a sponsored message placed below the newest one.
 */
@Composable
internal fun FollowNewestMessage(
    newestKey: Any?,
    newestMessageKey: Any?,
    reachedEnd: Boolean,
    listState: LazyListState,
) {
    var shown by remember { mutableStateOf(Triple(newestKey, newestMessageKey, reachedEnd)) }
    LaunchedEffect(newestKey, newestMessageKey, reachedEnd) {
        val (previousKey, previousMessageKey, previousReachedEnd) = shown
        shown = Triple(newestKey, newestMessageKey, reachedEnd)
        val changed = newestKey != previousKey || newestMessageKey != previousMessageKey
        if (!changed || !previousReachedEnd || !reachedEnd) return@LaunchedEffect
        // Entries may have come with it (a date separator), so the previous newest ones are looked up by key.
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.any { it.key == previousKey || it.key == previousMessageKey }) listState.requestScrollToItem(0)
    }
}

/** The key of the newest entry that is not a sponsored message; null if there is none. */
internal fun List<ChatItem>.newestMessageKey(): Any? = firstOrNull { it !is ChatItem.Sponsored }?.key

/** How close to the oldest or newest loaded message the list asks for more: about half a page of entries ahead. */
internal const val LOAD_MORE_THRESHOLD = 20
