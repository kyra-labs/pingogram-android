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
 * the list gives only a little. Released past [COMMIT_FRACTION] of the width (a fling counts where it would land),
 * [onSelect] selects the neighbour's tab at once, and the two settle on the default spatial spring from the release's
 * speed, as a tab tap moves them (`docs/DESIGN_SYSTEM.md` §5.1, Q-07): the selected list takes the neighbour's place
 * without a frame between ([rememberShownList]), and a finger that catches it on its way keeps the folder it chose.
 * Otherwise they spring back on the default spatial spring. A `TextHandleMove` haptic marks each change of what a
 * release would do, as the finger crosses the threshold and, for a fling that lands on the other side of it, at the
 * release. Screen readers and keyboards switch folders with the tabs.
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
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    // Whether a release where the list is now would change the folder, as the last haptic said; not observed.
    val armed = remember { BooleanArray(1) }

    fun commitsAt(position: Float): Boolean {
        val target = if (position > 0f) currentNeighbours.previous else currentNeighbours.next
        return target != null && position != 0f && abs(position) > folderSwitch.width * COMMIT_FRACTION
    }

    fun signal(commits: Boolean) {
        if (commits != armed[0]) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        armed[0] = commits
    }
    val dragState =
        rememberDraggableState { delta ->
            // Where the list is, also while a folder selected under the finger moved it ([rememberShownList]).
            val moved = folderSwitch.position + delta
            val target = if (moved > 0f) currentNeighbours.previous else currentNeighbours.next
            folderSwitch.drag += if (target == null) delta * EDGE_RESISTANCE else delta
            signal(commitsAt(folderSwitch.position))
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
                // Caught while it springs back or slides: the finger takes it from where it is, against the list it
                // slid against or a neighbour.
                folderSwitch.dragging = true
                folderSwitch.drag = folderSwitch.position
                folderSwitch.offset.snapTo(0f)
                armed[0] = commitsAt(folderSwitch.position)
                folderSwitch.followDrag()
            },
            onDragStopped = { velocity ->
                folderSwitch.dragging = false
                // The finger's movement and whatever a selection meanwhile moved the list by.
                val current = folderSwitch.position
                folderSwitch.offset.snapTo(current)
                folderSwitch.drag = 0f
                val width = folderSwitch.width
                val toward = sign(current)
                val target = if (toward > 0f) currentNeighbours.previous else currentNeighbours.next
                val landing = current + velocity * FLING_PROJECTION_SECONDS
                val commits =
                    toward != 0f && target != null && sign(landing) == toward && abs(landing) > width * COMMIT_FRACTION
                signal(commits)
                armed[0] = false
                if (target != null && commits) {
                    // The neighbour stays as it is now, whatever the neighbours become once its tab is selected.
                    val lists = currentNeighbourLists()
                    folderSwitch.fixed = (if (toward > 0f) lists.previous else lists.next)?.state ?: folderSwitch.fixed
                    // Selected now, not once the slide ends: a finger that catches the list on its way keeps it.
                    folderSwitch.swiped = true
                    currentOnSelect(target.folderId)
                    // Until the selection lands, when the selected list takes over the slide with its speed.
                    folderSwitch.offset.animateTo(toward * width, spec, initialVelocity = velocity)
                } else {
                    folderSwitch.offset.animateTo(0f, spec, initialVelocity = velocity)
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
