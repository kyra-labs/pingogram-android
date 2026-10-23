package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Scrolls [listState] back to its top when rows appear above the first one ([rowsAbove] grows: the story rings, the
 * archive row) while the list showed its top: the list keeps the row that was first in place, which would hide them.
 */
@Composable
internal fun ShowRowsAddedAtTop(
    listState: LazyListState,
    rowsAbove: Int,
) {
    var shownRowsAbove by remember { mutableIntStateOf(rowsAbove) }
    LaunchedEffect(rowsAbove) {
        val added = rowsAbove - shownRowsAbove
        shownRowsAbove = rowsAbove
        if (added > 0 && listState.firstVisibleItemIndex == added && listState.firstVisibleItemScrollOffset == 0) {
            listState.scrollToItem(0)
        }
    }
}
