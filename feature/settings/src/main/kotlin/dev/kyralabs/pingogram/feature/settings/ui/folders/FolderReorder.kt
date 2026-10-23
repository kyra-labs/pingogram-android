package dev.kyralabs.pingogram.feature.settings.ui.folders

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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

// Dragging the folders of the Chat folders page into a new order: the row follows the finger in its graphics layer,
// and passes the rows it crosses, which slide into its place on the list's placement springs.

/** The key of folder [id]'s row in the page's lazy list. */
internal fun folderRowKey(id: Int): String = "$FOLDER_KEY_PREFIX$id"

private const val FOLDER_KEY_PREFIX = "folder-"

/**
 * The order of the folders while one is dragged: [order] (the folders' ids, the dragged one where the finger has
 * taken it so far), the dragged folder ([draggedId]) and how far its row is from its place in the list ([offset],
 * read in the draw phase). [onDrop] gets the order the finger left, once it lifts.
 */
@Stable
internal class FolderReorderState(
    private val listState: LazyListState,
    private val haptics: HapticFeedback,
    folderIds: List<Int>,
    private val onDrop: (List<Int>) -> Unit,
) {
    var order: List<Int> by mutableStateOf(folderIds)
        private set

    var draggedId: Int? by mutableStateOf(null)
        private set

    var offset: Float by mutableFloatStateOf(0f)
        private set

    fun start(id: Int) {
        draggedId = id
        offset = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun drag(delta: Float) {
        val id = draggedId ?: return
        offset += delta
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.firstOrNull { it.key == folderRowKey(id) } ?: return
        val center = dragged.offset + offset + dragged.size / 2f
        val target =
            items.firstOrNull { item ->
                item.key != dragged.key &&
                    (item.key as? String)?.startsWith(FOLDER_KEY_PREFIX) == true &&
                    center >= item.offset &&
                    center < item.offset + item.size
            } ?: return
        val targetId = order.firstOrNull { folderRowKey(it) == target.key } ?: return
        val from = order.indexOf(id)
        val to = order.indexOf(targetId)
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        // The row takes the target's place in the next layout: keep it under the finger.
        offset += dragged.offset - target.offset
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    fun end() {
        if (draggedId == null) return
        draggedId = null
        offset = 0f
        onDrop(order)
    }
}

/** A [FolderReorderState] for the folders [folderIds], begun anew whenever they change. */
@Composable
internal fun rememberFolderReorderState(
    listState: LazyListState,
    folderIds: List<Int>,
    onDrop: (List<Int>) -> Unit,
): FolderReorderState {
    val haptics = LocalHapticFeedback.current
    val currentOnDrop by rememberUpdatedState(onDrop)
    return remember(listState, haptics, folderIds) {
        FolderReorderState(listState, haptics, folderIds) { currentOnDrop(it) }
    }
}

/** Drags folder [id]'s row after a long press anywhere on it. */
internal fun Modifier.dragAfterLongPress(
    state: FolderReorderState,
    id: Int,
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

/** Drags folder [id]'s row as soon as its handle moves. */
internal fun Modifier.dragHandle(
    state: FolderReorderState,
    id: Int,
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
