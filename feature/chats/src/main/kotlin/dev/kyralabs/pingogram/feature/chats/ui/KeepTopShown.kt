package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/**
 * Keeps a list that shows its top at its top when another row comes first ([firstKey] changes: the story rings or the
 * archive row appear, a chat moves up, chats load above the first one). A lazy list keeps the row that was first in
 * place and scrolls the new ones out of sight above it, which hid them, and showed a folder's list scrolled to a chat
 * further down when its first page came in after a part of it.
 *
 * Checked after the composition that brings the rows and before their layout, where [listState] still says where the
 * list was, so the list lays the new first row out at once, in the same frame.
 */
@Composable
internal fun KeepTopShown(
    listState: LazyListState,
    firstKey: Any?,
) {
    val shown = remember(listState) { ShownFirstKey(firstKey) }
    SideEffect {
        if (shown.key != firstKey) {
            shown.key = firstKey
            val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            if (atTop && !listState.isScrollInProgress) listState.requestScrollToItem(0)
        }
    }
}

/** The first row's key the list was last composed with; not observed. */
private class ShownFirstKey(
    var key: Any?,
)
