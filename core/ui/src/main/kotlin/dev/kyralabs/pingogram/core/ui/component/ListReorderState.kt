package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

// Dragging the rows of a lazy list into a new order (chat folders, sticker sets, a forum's pinned topics): the row
// follows the finger in its graphics layer, and passes the rows it crosses, which slide into its place on the list's
// placement springs.

/**
 * The order of a list's rows while one is dragged: [order] (the rows' ids, the dragged one where the finger has taken
 * it so far), the dragged row ([draggedId]) and how far it is from its place in the list ([offset], read in the draw
 * phase). The rows' keys in the lazy list are [rowKey] of their ids. [onDrop] gets the order the finger left, once it
 * lifts.
 */
@Stable
class ListReorderState<K : Any>(
    private val listState: LazyListState,
    private val haptics: HapticFeedback,
    ids: List<K>,
    private val rowKey: (K) -> Any,
    private val onDrop: (List<K>) -> Unit,
) {
    var order: List<K> by mutableStateOf(ids)
        private set

    var draggedId: K? by mutableStateOf(null)
        private set

    var offset: Float by mutableFloatStateOf(0f)
        private set

    private val rowKeys get() = order.mapTo(HashSet(), rowKey)

    fun start(id: K) {
        draggedId = id
        offset = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun drag(delta: Float) {
        val id = draggedId ?: return
        offset += delta
        val (dragged, target) = swapTarget(id) ?: return
        val from = order.indexOf(id)
        val to = order.indexOfFirst { rowKey(it) == target.key }
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        // The row takes the target's place in the next layout: keep it under the finger.
        offset += dragged.offset - target.offset
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    /** The dragged row [id] and the row of this list under its centre, if it passed over another one. */
    private fun swapTarget(id: K): Pair<LazyListItemInfo, LazyListItemInfo>? {
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.firstOrNull { it.key == rowKey(id) }
        val center = dragged?.let { it.offset + offset + it.size / 2f }
        val keys = rowKeys
        val target =
            center?.let {
                items.firstOrNull { item ->
                    item.key != dragged.key &&
                        item.key in keys &&
                        center >= item.offset &&
                        center < item.offset + item.size
                }
            }
        return if (dragged != null && target != null) dragged to target else null
    }

    fun end() {
        if (draggedId == null) return
        draggedId = null
        offset = 0f
        onDrop(order)
    }
}

/** A [ListReorderState] for the rows [ids] (keyed [rowKey] in the list), begun anew whenever they change. */
@Composable
fun <K : Any> rememberListReorderState(
    listState: LazyListState,
    ids: List<K>,
    rowKey: (K) -> Any,
    onDrop: (List<K>) -> Unit,
): ListReorderState<K> {
    val haptics = LocalHapticFeedback.current
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentRowKey by rememberUpdatedState(rowKey)
    return remember(listState, haptics, ids) {
        ListReorderState(listState, haptics, ids, { currentRowKey(it) }) { currentOnDrop(it) }
    }
}

/** Drags row [id] after a long press anywhere on it. */
fun <K : Any> Modifier.dragAfterLongPress(
    state: ListReorderState<K>,
    id: K,
): Modifier =
    pointerInput(state, id) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.start(id) },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y)
            },
            onDragEnd = state::end,
            onDragCancel = state::end,
        )
    }

/** Drags row [id] as soon as its handle moves. */
fun <K : Any> Modifier.dragHandle(
    state: ListReorderState<K>,
    id: K,
): Modifier =
    pointerInput(state, id) {
        detectDragGestures(
            onDragStart = { state.start(id) },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y)
            },
            onDragEnd = state::end,
            onDragCancel = state::end,
        )
    }
