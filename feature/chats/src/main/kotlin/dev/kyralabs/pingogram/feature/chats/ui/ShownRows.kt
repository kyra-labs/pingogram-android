package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * While the list is the one shown ([active]), tells [onEvent] which chats' rows it shows ([ChatListEvent.RowsShown]),
 * each time the rows on screen change: the first or last visible row, or how many rows there are. A scroll within a row
 * reads only the layout, without a new set.
 */
@Composable
internal fun ReportShownRows(
    listState: LazyListState,
    active: Boolean,
    onEvent: (ChatListEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState, active) {
        if (!active) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val visible = layout.visibleItemsInfo
            ShownRange(visible.firstOrNull()?.index ?: -1, visible.lastOrNull()?.index ?: -1, layout.totalItemsCount)
        }.distinctUntilChanged()
            .collect {
                val chatIds =
                    listState.layoutInfo.visibleItemsInfo.mapNotNullTo(
                        HashSet(),
                    ) { item -> item.key as? Long }
                currentOnEvent(ChatListEvent.RowsShown(chatIds))
            }
    }
}

/** The rows a list shows: the first and last visible, of [total]. */
private data class ShownRange(
    val first: Int,
    val last: Int,
    val total: Int,
)
