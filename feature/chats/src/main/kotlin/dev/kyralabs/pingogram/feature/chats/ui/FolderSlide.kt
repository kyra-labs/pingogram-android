package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.sign

/**
 * A folder's list beside the selected one, which a swipe pulls in ([folderSwipe]): [state] as its list would show it,
 * [folderId] null for "All chats".
 */
@Immutable
data class FolderNeighbourList(
    val folderId: Int?,
    val state: ChatListUiState,
)

/**
 * The lists of the folder tabs beside the selected one ([FolderNeighbours]): [previous] before it, [next] after it;
 * null at either end of the tabs and without tabs. Their first page is loaded ahead, so a swipe shows their chats under
 * the finger ([ChatListViewModel.neighbourLists]).
 */
@Immutable
data class FolderNeighbourLists(
    val previous: FolderNeighbourList? = null,
    val next: FolderNeighbourList? = null,
)

/**
 * How the chat list moves between folders (`docs/DESIGN_SYSTEM.md` §5, §6 "Folder tabs", "Swipe actions"; owner
 * decision 2026-11-07): like a pager, the list shown and the folder's list it slides against ([FolderCompanion]) move
 * together, side by side, [position] being the shown list's place toward the end, the companion a list's width away on
 * its [side]. Read in the draw phase ([folderSlide]), so neither a drag nor a slide recomposes; only the companion
 * coming, going or changing sides composes.
 *
 * The rows do not animate ([animateRows]) while the selected folder's rows replace the others: the two lists have
 * nothing to animate between, and the list would otherwise slide the chats both lists have from where the old one
 * showed them, and fade the others in and out, all started in the frame that composes the new rows.
 */
@Stable
internal class FolderSwitch {
    /** The list's opacity, which a folder switch crossfades with reduced motion instead of sliding. */
    val alpha = Animatable(1f)
    var animateRows by mutableStateOf(true)

    /** How far the finger moved the list toward the end, in pixels, while it swipes it ([folderSwipe]). */
    var drag by mutableFloatStateOf(0f)

    /** The list's movement toward the end apart from the finger, in pixels: sliding away, in or back. */
    val offset = Animatable(0f)

    /** Where the list is, toward the end of [drag] and [offset]; read in the draw phase. */
    val position: Float
        get() = drag + offset.value

    /** The list's width, in pixels, which a slide covers; set as it is laid out, read only by gestures and slides. */
    var width = 0

    /**
     * Where the companion is: -1 a width toward the start of the list shown (the folder before it, which a swipe toward
     * the end brings), 1 toward the end, 0 none.
     */
    var side by mutableIntStateOf(0)

    /**
     * The companion's state while it is no live neighbour ([FolderNeighbourLists]): the folder a swipe brought in until
     * the selection reaches it, then the folder sliding away; null for a live neighbour.
     */
    var fixed by mutableStateOf<ChatListUiState?>(null)

    /** Where [fixed]'s list was scrolled, as its first visible row and the offset past it. */
    var fixedScroll: Pair<Int, Int> = 0 to 0

    /** A swipe selected the folder, whose list already shows as the companion; read once by [rememberShownList]. */
    var swiped = false

    /** Where each folder's list was left, for a neighbour to show there; [rememberShownList] keeps them. */
    var positions = FolderPositions()

    /** The companion's side as a live drag reveals it: before the list shown toward the end, after it otherwise. */
    fun followDrag() {
        if (fixed != null) return
        val moved = sign(position)
        if (moved != 0f) side = -moved.toInt()
    }

    /** No companion any more. */
    fun clearCompanion() {
        side = 0
        fixed = null
    }

    /**
     * Puts the newly selected folder's list where it comes in from, with [outgoing], the folder's list shown before
     * (scrolled to [scroll]), as the companion it slides against: after a swipe ([swiped]) where the companion showed
     * it, the old list taking the companion's side; after a tap a width toward the side of its tab ([toward]: 1 the
     * end, -1 the start); in place, without a companion, for neither (reduced motion, a tab that is gone).
     */
    suspend fun bringIn(
        outgoing: ChatListUiState,
        scroll: Pair<Int, Int>,
        toward: Int,
        swiped: Boolean,
    ) {
        val start =
            when {
                swiped -> position + side * width
                else -> (toward * width).toFloat()
            }
        val companionSide = if (swiped) -side else -toward
        drag = 0f
        offset.snapTo(start)
        if (companionSide == 0) {
            clearCompanion()
        } else {
            fixedScroll = scroll
            fixed = outgoing
            side = companionSide
        }
    }
}

/**
 * Draws the list where [folderSwitch] puts it, and fades each drawing of it on its own
 * ([CompositingStrategy.ModulateAlpha]) rather than the list as a whole in an offscreen buffer the size of the screen:
 * the rows draw opaque containers in the screen's own color, so the two look the same.
 */
internal fun Modifier.folderSlide(
    folderSwitch: FolderSwitch,
    isRtl: Boolean,
    side: Int = 0,
): Modifier =
    graphicsLayer {
        alpha = folderSwitch.alpha.value
        val x = folderSwitch.position + side * folderSwitch.width
        translationX = if (isRtl) -x else x
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }

/**
 * The folder's list the shown one slides against ([FolderSwitch.side]): a neighbour under a swipe ([neighbours], loaded
 * ahead), the folder a swipe brought in until it is selected, or the folder sliding away after a switch. Composed only
 * while it is on screen, at the place where it was left ([FolderPositions], the top for a folder not shown yet;
 * [hasStories]: whether the list starts with the story rings), and drawn in the draw phase beside the list. [content]
 * draws it, given its state and list state and the modifier that places it; it takes no input and is hidden from
 * screen readers, which switch folders with the tabs.
 */
@Composable
internal fun FolderCompanion(
    folderSwitch: FolderSwitch,
    neighbours: () -> FolderNeighbourLists,
    isRtl: Boolean,
    hasStories: Boolean,
    content: @Composable (state: ChatListUiState, listState: LazyListState, modifier: Modifier) -> Unit,
) {
    val side = folderSwitch.side
    if (side == 0) return
    val fixed = folderSwitch.fixed
    val state =
        fixed ?: neighbours().let { if (side < 0) it.previous else it.next }?.state ?: return
    val listState =
        remember(state.selectedFolderId, fixed != null) {
            val (index, offset) =
                fixed?.let { folderSwitch.fixedScroll }
                    ?: folderSwitch.positions.scrollOf(state, hasStories)
            LazyListState(index, offset)
        }
    content(state, listState, Modifier.folderSlide(folderSwitch, isRtl, side).clearAndSetSemantics {})
}

/**
 * The state whose list shows: [state], with the list of the folder before it sliding away beside it after a folder
 * was selected ([FolderSwitch]). The selected folder's list comes in from the side of its tab (from the end for a tab
 * after the one shown, mirrored right to left) while the old one leaves toward the other, both on the motion scheme's
 * default spatial spring; after a swipe the selected list is already beside the old one, where the finger left it, and
 * settles from there. It shows where the user left it ([FolderPositions]). The tabs follow the selection at once.
 * Selecting yet another folder meanwhile carries on from there. Where the tab shown before is gone the lists
 * crossfade on the effects springs instead; with reduced motion (animations off) they swap at once, as Compose would
 * end any fade at once.
 *
 * The folder shown and where each folder was left are saved, so a rotation or process death keeps them.
 */
@Composable
internal fun rememberShownList(
    state: ChatListUiState,
    listState: LazyListState,
    folderSwitch: FolderSwitch,
    hasStories: Boolean,
): ChatListUiState {
    var shownFolderId by rememberSaveable { mutableStateOf(state.selectedFolderId) }
    val positions = rememberSaveable(saver = FolderPositions.Saver) { FolderPositions() }
    folderSwitch.positions = positions
    // The last state of the folder shown, for its list to slide away with; read only then, so updating it recomposes
    // nothing.
    val lastShown = remember { mutableStateOf(state) }
    val isShown = state.selectedFolderId == shownFolderId
    SideEffect { if (isShown) lastShown.value = state }
    val currentState by rememberUpdatedState(state)
    val currentHasStories by rememberUpdatedState(hasStories)
    val motionScheme = MaterialTheme.motionScheme
    LaunchedEffect(state.selectedFolderId) {
        // The system's animator duration scale, which Compose applies to every animation; 0 is reduced motion.
        val animated = coroutineContext[MotionDurationScale]?.scaleFactor != 0f
        val swiped = folderSwitch.swiped
        folderSwitch.swiped = false
        if (state.selectedFolderId != shownFolderId) {
            val outgoing = lastShown.value
            val toward = if (swiped) 0 else tabDirection(state, from = shownFolderId)
            // A tab that is gone has no side to slide from: the lists crossfade. With reduced motion Compose would end
            // the fade at once anyway, and waiting for it would leave the list blank for a frame: they swap at once.
            if (!swiped && animated && toward == 0) {
                folderSwitch.alpha.animateTo(0f, motionScheme.fastEffectsSpec())
            }
            positions.save(shownFolderId, listState)
            val scroll = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            folderSwitch.animateRows = false
            shownFolderId = state.selectedFolderId
            // Applied in the layout pass that brings the selected folder's rows, so the list does not keep the row
            // that was first in the old list.
            positions.restore(currentState, currentHasStories, listState)
            folderSwitch.bringIn(outgoing, scroll, toward = if (animated) toward else 0, swiped)
        }
        coroutineScope {
            // Rows animate again once the selected folder's rows are laid out, in the frame that composes them.
            if (!folderSwitch.animateRows) {
                launch {
                    repeat(ROWS_LAYOUT_FRAMES) { withFrameNanos {} }
                    folderSwitch.animateRows = true
                }
            }
            launch { folderSwitch.alpha.animateTo(1f, motionScheme.defaultEffectsSpec()) }
            folderSwitch.offset.animateTo(0f, motionScheme.defaultSpatialSpec())
            if (folderSwitch.fixed != null) folderSwitch.clearCompanion()
        }
    }
    return if (isShown) state else lastShown.value
}

/**
 * Which side the tab of [state]'s selected folder lies on from the tab of the folder [from]: 1 after it (the new list
 * comes in from the end), -1 before it, 0 where either tab is gone.
 */
private fun tabDirection(
    state: ChatListUiState,
    from: Int?,
): Int {
    val ids = state.folderTabs.map { (it as? FolderTab.Folder)?.id }
    val old = ids.indexOf(from)
    val new = ids.indexOf(state.selectedFolderId)
    return if (old < 0 || new < 0) 0 else (new - old).sign
}

/**
 * Where the user left each folder's list (`null` for "All chats"): its first visible row and how far it is scrolled
 * past it. A row is found again by its key, wherever it moved since; one that left the list gives way to the row now at
 * its index. A folder not shown before starts at its top. Not observed: only the folder switch reads and writes it.
 */
internal class FolderPositions private constructor(
    private val positions: MutableMap<Int?, ListPosition>,
) {
    constructor() : this(HashMap())

    /** Keeps where [folderId]'s list, shown by [listState], is now. */
    fun save(
        folderId: Int?,
        listState: LazyListState,
    ) {
        val index = listState.firstVisibleItemIndex
        // Not the first of visibleItemsInfo, which may be a row drawn in the content padding behind the top bar.
        val first = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        positions[folderId] = ListPosition(first.key, index, listState.firstVisibleItemScrollOffset)
    }

    /**
     * Puts the list of [state]'s folder where it was left, or at its top, in the next layout pass ([scrollOf]).
     */
    fun restore(
        state: ChatListUiState,
        hasStories: Boolean,
        listState: LazyListState,
    ) {
        val (index, offset) = scrollOf(state, hasStories)
        listState.requestScrollToItem(index, offset)
    }

    /**
     * Where the list of [state]'s folder was left, as its first visible row and the offset past it; its top for a
     * folder not shown before. The story rings ([hasStories]), the archive row and the "Pinned" header come first.
     */
    fun scrollOf(
        state: ChatListUiState,
        hasStories: Boolean,
    ): Pair<Int, Int> {
        val position = positions[state.selectedFolderId] ?: return 0 to 0
        val content = state.content as? ChatListContent.Content
        val items = content?.items.orEmpty()
        val hasPinned = (content?.pinnedCount ?: 0) > 0
        val storyRows = if (hasStories) 1 else 0
        val headerRows = storyRows + if (state.archive != null) 1 else 0
        val rowsAbove = headerRows + if (hasPinned) 1 else 0
        val index =
            when (val key = position.key) {
                STORIES_ROW -> 0.takeIf { hasStories }
                ARCHIVE_ROW -> storyRows.takeIf { state.archive != null }
                PINNED_HEADER -> headerRows.takeIf { hasPinned }
                else -> items.indexOfFirst { it.id == key }.takeIf { it >= 0 }?.plus(rowsAbove)
            }
        return if (index != null) index to position.offset else position.index to 0
    }

    companion object {
        /** Each position as four values: the folder ([NO_FOLDER] for "All chats"), the row's key, index and offset. */
        val Saver =
            listSaver<FolderPositions, Any>(
                save = { saved ->
                    saved.positions.flatMap { (folderId, position) ->
                        listOf(folderId ?: NO_FOLDER, position.key, position.index, position.offset)
                    }
                },
                restore = { values ->
                    val positions =
                        values.chunked(VALUES_PER_POSITION).associateTo(HashMap()) { saved ->
                            val folderId = (saved[0] as Int).takeIf { it != NO_FOLDER }
                            folderId to ListPosition(key = saved[1], index = saved[2] as Int, offset = saved[3] as Int)
                        }
                    FolderPositions(positions)
                },
            )

        /** Stands for "All chats" when saved; TDLib's folder ids are positive. */
        private const val NO_FOLDER = -1
        private const val VALUES_PER_POSITION = 4
    }
}

/**
 * A list's first visible row: its [key] (a chat id, [STORIES_ROW] or [ARCHIVE_ROW]), its [index], and the [offset]
 * scrolled past it.
 */
private class ListPosition(
    val key: Any,
    val index: Int,
    val offset: Int,
)

/**
 * How many frames rows do not animate for after a folder switch: enough for the frame that composes and lays out the
 * selected folder's rows, whether the switch is made during a frame (after the fade-out) or between two (without it).
 */
private const val ROWS_LAYOUT_FRAMES = 2
