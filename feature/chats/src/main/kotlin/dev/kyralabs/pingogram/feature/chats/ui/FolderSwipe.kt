package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.sign

/** A folder tab a swipe of the list shows: [folderId], null for "All chats". */
@Immutable
internal data class FolderTarget(
    val folderId: Int?,
)

/**
 * The folder tabs beside the selected one, which swiping the list shows: [previous] for a swipe toward the end (right,
 * left to right), [next] for one toward the start; null at either end of the tabs and without tabs.
 */
@Immutable
internal data class FolderNeighbours(
    val previous: FolderTarget? = null,
    val next: FolderTarget? = null,
) {
    companion object {
        /** The tabs beside [selectedFolderId] (null: "All chats") among [tabs]. */
        fun of(
            tabs: List<FolderTab>,
            selectedFolderId: Int?,
        ): FolderNeighbours {
            val index = tabs.indexOfFirst { it.folderId == selectedFolderId }
            if (index < 0) return FolderNeighbours()
            return FolderNeighbours(
                previous = tabs.getOrNull(index - 1)?.let { FolderTarget(it.folderId) },
                next = tabs.getOrNull(index + 1)?.let { FolderTarget(it.folderId) },
            )
        }

        private val FolderTab.folderId: Int?
            get() = (this as? FolderTab.Folder)?.id
    }
}

/**
 * [folderSwipe] for the chat list in [uiState], its neighbours' lists from [neighbourLists]: only on the Chats tab with
 * folder tabs, not in selection mode, and only while the user chose it ([ChatSwipes.changesFolder]).
 */
@Composable
internal fun Modifier.folderSwipe(
    uiState: ChatListUiState,
    folderSwitch: FolderSwitch,
    neighbourLists: () -> FolderNeighbourLists,
    onEvent: (ChatListEvent) -> Unit,
): Modifier {
    val neighbours =
        remember(uiState.folderTabs, uiState.selectedFolderId) {
            FolderNeighbours.of(uiState.folderTabs, uiState.selectedFolderId)
        }
    return folderSwipe(
        enabled =
            LocalChatSwipes.current.changesFolder && uiState.kind == ChatListKind.Chats &&
                uiState.selection == null && uiState.folderTabs.isNotEmpty(),
        folderSwitch = folderSwitch,
        neighbours = neighbours,
        neighbourLists = neighbourLists,
        onSelect = { onEvent(ChatListEvent.SelectFolder(it)) },
    )
}

/**
 * Swiping the whole list sideways to the folder tab beside the selected one ([ChatSwipes.changesFolder]), like a pager
 * (owner decision 2026-11-07): the list follows the finger and the folder's list beside it comes in with it
 * ([FolderSwitch.position], [FolderCompanion] from [neighbourLists], whose first page is loaded ahead), both drawn in
 * their graphics layers, so a drag never recomposes the list. Toward the end it shows the tab before the selected one
 * ([FolderNeighbours.previous]), toward the start the one after it, mirrored right to left; where there is no such tab
 * the list gives only a little. Released past [COMMIT_FRACTION] of the width (a fling counts where it would land), the
 * two settle on the fast spatial spring, the neighbour in the list's place, and [onSelect] selects its tab, whose list
 * then takes the neighbour's place without a frame between ([rememberShownList]); otherwise they spring back on the
 * default spatial spring. A `TextHandleMove` haptic marks the threshold each way, as a row's swipe does. Screen readers
 * and keyboards switch folders with the tabs.
 */
@Composable
internal fun Modifier.folderSwipe(
    enabled: Boolean,
    folderSwitch: FolderSwitch,
    neighbours: FolderNeighbours,
    neighbourLists: () -> FolderNeighbourLists,
    onSelect: (folderId: Int?) -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentNeighbours by rememberUpdatedState(neighbours)
    val currentNeighbourLists by rememberUpdatedState(neighbourLists)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val motionScheme = MaterialTheme.motionScheme
    val back = motionScheme.defaultSpatialSpec<Float>()
    val away = motionScheme.fastSpatialSpec<Float>()
    val dragState =
        rememberDraggableState { delta ->
            val current = folderSwitch.drag
            val moved = current + delta
            val target = if (moved > 0f) currentNeighbours.previous else currentNeighbours.next
            val next = if (target == null) current + delta * EDGE_RESISTANCE else moved
            val threshold = folderSwitch.width * COMMIT_FRACTION
            if (target != null && (abs(current) < threshold) != (abs(next) < threshold)) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            folderSwitch.drag = next
            folderSwitch.followDrag()
        }
    return onSizeChanged { folderSwitch.width = it.width }
        .draggable(
            state = dragState,
            orientation = Orientation.Horizontal,
            enabled = enabled,
            // Positive toward the end in both directions: right in left-to-right layouts, left in right-to-left.
            reverseDirection = isRtl,
            onDragStarted = {
                // Caught while it springs back or slides: the finger takes it from where it is, against a neighbour.
                folderSwitch.drag = folderSwitch.position
                folderSwitch.offset.snapTo(0f)
                folderSwitch.fixed = null
                folderSwitch.followDrag()
            },
            onDragStopped = { velocity ->
                val current = folderSwitch.drag
                folderSwitch.offset.snapTo(current)
                folderSwitch.drag = 0f
                val width = folderSwitch.width
                val toward = sign(current)
                val target = if (toward > 0f) currentNeighbours.previous else currentNeighbours.next
                val landing = current + velocity * FLING_PROJECTION_SECONDS
                val commits = sign(landing) == toward && abs(landing) > width * COMMIT_FRACTION
                if (toward != 0f && target != null && commits) {
                    // The neighbour stays as it is now, whatever the neighbours become once its tab is selected.
                    val lists = currentNeighbourLists()
                    folderSwitch.fixed = (if (toward > 0f) lists.previous else lists.next)?.state
                    folderSwitch.offset.animateTo(toward * width, away, initialVelocity = velocity)
                    folderSwitch.swiped = true
                    currentOnSelect(target.folderId)
                } else {
                    folderSwitch.offset.animateTo(0f, back, initialVelocity = velocity)
                    folderSwitch.clearCompanion()
                }
            },
        )
}

/** How much of the list's width a swipe must cross to change the folder. */
private const val COMMIT_FRACTION = 0.3f

/** How far a fling carries the list, in seconds of its speed at release, for deciding whether it changes the folder. */
private const val FLING_PROJECTION_SECONDS = 0.15f

/** How much of the finger's movement the list follows toward a side without a folder tab. */
private const val EDGE_RESISTANCE = 0.2f
