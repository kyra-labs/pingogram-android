package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.kyralabs.pingogram.core.ui.gesture.SettlingOffset
import kotlinx.coroutines.CoroutineScope

// Dragging the rows of a lazy list into a new order (chat folders, sticker sets, a forum's pinned topics): the row
// follows the finger in its graphics layer, and passes the rows it crosses, which slide into its place on the list's
// placement springs. Dropped, it settles into its own place on the theme's default spatial spring, from the finger's
// velocity (docs/DESIGN_SYSTEM.md §5: list reordering).

/**
 * The order of a list's rows while one is dragged: [order] (the rows' ids, the dragged one where the finger has taken
 * it so far), the dragged row ([draggedId]) and how far it is from its place in the list ([offset], read in the draw
 * phase). The rows' keys in the lazy list are [rowKey] of their ids. [onDrop] gets the order the finger left as it
 * lifts.
 *
 * Once dropped, the row stays [draggedId] while its [offset] settles to 0 on [settleSpec] in [scope], starting at the
 * finger's velocity, so it keeps its place above the others and its placement stays off until it is in its slot. A
 * new drag takes over from the settle: of the same row, from where it is; of another, with the settling row in its
 * place at once.
 */
@Suppress("LongParameterList") // The list, its rows and their keys, haptics, and where and how a drop settles.
@Stable
class ListReorderState<K : Any>(
    private val listState: LazyListState,
    private val haptics: HapticFeedback,
    ids: List<K>,
    private val rowKey: (K) -> Any,
    private val scope: CoroutineScope,
    private val settleSpec: AnimationSpec<Float>,
    private val onDrop: (List<K>) -> Unit,
) {
    var order: List<K> by mutableStateOf(ids)
        private set

    var draggedId: K? by mutableStateOf(null)
        private set

    private val settling = SettlingOffset()

    /** How far the dragged (or settling) row is from its place in the list. */
    val offset: Float get() = settling.value

    /** The ids the list last gave, which [order] follows whenever no finger holds a row. */
    private var source = ids

    /** Whether a finger holds the row, rather than the row settling after a drop. */
    private var dragging = false

    private val velocity = VelocityTracker1D(isDataDifferential = true)

    private val rowKeys get() = order.mapTo(HashSet(), rowKey)

    /** Takes the list's [ids] as they change: a change while a finger holds a row ends that drag without a drop. */
    internal fun update(ids: List<K>) {
        if (ids == source) return
        source = ids
        if (dragging) {
            dragging = false
            draggedId = null
            settling.snapTo(0f)
        }
        order = ids
    }

    fun start(id: K) {
        if (draggedId == id) {
            // Caught while it settles: the finger takes it from where it is.
            settling.snapTo(settling.value)
        } else {
            draggedId = id
            settling.snapTo(0f)
        }
        dragging = true
        velocity.resetTracking()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /** Moves the dragged row by [delta]; [uptimeMillis], the event's time, gives the drop its velocity. */
    fun drag(
        delta: Float,
        uptimeMillis: Long? = null,
    ) {
        val id = draggedId?.takeIf { dragging } ?: return
        uptimeMillis?.let { velocity.addDataPoint(it, delta) }
        settling.dragBy(delta)
        val (dragged, target) = swapTarget(id) ?: return
        val from = order.indexOf(id)
        val to = order.indexOfFirst { rowKey(it) == target.key }
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        // The row takes the target's place in the next layout: keep it under the finger.
        settling.dragBy(dragged.offset.toFloat() - target.offset)
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
        val id = draggedId?.takeIf { dragging } ?: return
        dragging = false
        val release = velocity.calculateVelocity()
        onDrop(order)
        settling.settle(scope, 0f, settleSpec, velocity = release) {
            if (draggedId == id) draggedId = null
        }
    }
}

/** A [ListReorderState] for the rows [ids] (keyed [rowKey] in the list), which follows them as they change. */
@Composable
fun <K : Any> rememberListReorderState(
    listState: LazyListState,
    ids: List<K>,
    rowKey: (K) -> Any,
    onDrop: (List<K>) -> Unit,
): ListReorderState<K> {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val settleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentRowKey by rememberUpdatedState(rowKey)
    val state =
        remember(listState, haptics, settleSpec) {
            ListReorderState(listState, haptics, ids, { currentRowKey(it) }, scope, settleSpec) { currentOnDrop(it) }
        }
    state.update(ids)
    return state
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
                state.drag(amount.y, change.uptimeMillis)
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
                state.drag(amount.y, change.uptimeMillis)
            },
            onDragEnd = state::end,
            onDragCancel = state::end,
        )
    }
