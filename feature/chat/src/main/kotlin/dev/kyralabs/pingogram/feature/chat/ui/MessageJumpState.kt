package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Where the message list has gone ([ListAnchor]) and the message highlighted after a jump, read in the draw phase.
 *
 * @param highlightedId a message highlighted at full strength from the start, for previews and goldens.
 * @param movedToAnchor the id of the last anchor the list moved to; saved, so a rotation or process death does not move
 *   the list again ([rememberMessageJumpState]).
 */
@Stable
internal class MessageJumpState(
    highlightedId: Long? = null,
    movedToAnchor: Int = NO_ANCHOR,
) {
    /** The id of the last [ListAnchor] the list moved to, or [NO_ANCHOR]. */
    var movedToAnchor: Int by mutableIntStateOf(movedToAnchor)
        internal set

    private var highlightedId: Long? by mutableStateOf(highlightedId)
    private val strength = Animatable(if (highlightedId != null) 1f else 0f)

    /** Where the list puts an anchor's entry when it next measures ([anchorPlacement]), which reads it there. */
    internal var placement: AnchorPlacement? by mutableStateOf(null)
        private set

    /** The anchor of the last [placement] the list took, so it takes each once. */
    internal var appliedPlacement: Int = NO_ANCHOR

    /** The last anchor the list was measured at, placed there or found on screen: its layout shows the entry. */
    internal var laidOutAnchor: Int = NO_ANCHOR

    /** Whether the list glides to the last anchor taken up, whose entry was on screen. */
    internal var glides: Boolean = false
        private set

    private var takenAnchor: Int = NO_ANCHOR

    /**
     * Takes anchor [anchorId] up, once: the list glides there when its entry is on screen ([onScreen]), otherwise it
     * measures with the entry at [index] at [position] ([placement]).
     */
    internal fun take(
        anchorId: Int,
        onScreen: Boolean,
        index: Int,
        position: AnchorPosition,
    ) {
        if (takenAnchor == anchorId) return
        takenAnchor = anchorId
        glides = onScreen
        if (onScreen) laidOutAnchor = anchorId else placement = AnchorPlacement(anchorId, index, position)
    }

    /**
     * How strongly [bubble] is highlighted, 0 for none to 1. Reads snapshot state: call it in the draw phase, so the
     * highlight fading in and out only redraws.
     */
    fun highlightOf(bubble: ChatItem.Bubble): Float {
        val id = highlightedId ?: return 0f
        return if (bubble.showsMessage(id)) strength.value else 0f
    }

    /**
     * Highlights message [messageId]: fades in, holds for [HIGHLIGHT_HOLD_MILLIS] and fades out on the motion scheme's
     * effects spring. With animations off (reduced motion) the springs end at once, leaving a short static highlight.
     */
    suspend fun highlight(
        messageId: Long,
        motionScheme: MotionScheme,
    ) {
        try {
            strength.snapTo(0f)
            highlightedId = messageId
            strength.animateTo(1f, motionScheme.defaultEffectsSpec())
            delay(HIGHLIGHT_HOLD_MILLIS)
            strength.animateTo(0f, motionScheme.defaultEffectsSpec())
        } finally {
            highlightedId = null
        }
    }

    companion object {
        /** No anchor taken yet: the list moves to the first one it shows. */
        const val NO_ANCHOR = -1

        /** How long the highlight stays at full strength, long enough to find the message on screen. */
        private const val HIGHLIGHT_HOLD_MILLIS = 1_000L

        /** Keeps the anchor the list moved to; the highlight is not worth restoring. */
        val Saver: Saver<MessageJumpState, Int> =
            Saver(save = { it.movedToAnchor }, restore = { MessageJumpState(movedToAnchor = it) })
    }
}

@Composable
internal fun rememberMessageJumpState(): MessageJumpState =
    rememberSaveable(saver = MessageJumpState.Saver) { MessageJumpState() }

/** The entry shows message [messageId]: a bubble its own or one of its album's, a service message its own. */
internal fun ChatItem.showsMessage(messageId: Long): Boolean =
    when (this) {
        is ChatItem.Bubble -> id == messageId || album?.messageIds?.contains(messageId) == true
        is ChatItem.Service -> id == messageId
        is ChatItem.DateSeparator, is ChatItem.UnreadDivider -> false
    }

/**
 * Moves [listState] to [content]'s anchor, once per anchor id ([MessageJumpState.movedToAnchor]), outside the items
 * (`docs/PERFORMANCE.md` §4):
 *
 * - **Newest**, or the unread divider when the items have none: the start of the reverse layout, requested before the
 *   list measures these items, so they are laid out there in their first frame.
 * - **Unread divider**: near the top, so the unread messages follow it downwards ([AnchorPosition.NearTop]).
 * - **Message**: centred ([AnchorPosition.Centre]), and highlighted if the anchor says so; gliding there on the default
 *   spatial spring when it is on screen already.
 *
 * The last two are placed as the list measures ([anchorPlacement]), so it composes and measures the entries around
 * the anchor only, not the newest ones first and then those; then, the entry measured, it moves by what its height
 * changes. The list hides until then ([hideUntilAnchored]), one frame. Leading entries before the items (the newer
 * pages' indicator) shift every index by [leadingEntries].
 */
@Composable
internal fun FollowListAnchor(
    content: ChatContent.Content,
    listState: LazyListState,
    state: MessageJumpState,
    leadingEntries: Int,
) {
    val anchor = content.anchor
    val items = content.items
    val entryIndex = content.anchorEntryIndex()
    val pending = state.movedToAnchor != anchor.id
    if (pending && entryIndex < 0) {
        SideEffect {
            listState.requestScrollToItem(0)
            state.movedToAnchor = anchor.id
        }
    }
    val listIndex = entryIndex + leadingEntries
    val key = items.getOrNull(entryIndex)?.key
    val position = anchor.target.position
    if (pending && entryIndex >= 0) {
        // Before the list measures: on screen, it glides from there; otherwise it measures at the anchor.
        SideEffect { state.take(anchor.id, listState.layoutInfo.showsEntry(listIndex, key), listIndex, position) }
    }
    val currentItems by rememberUpdatedState(items)
    val currentLeading by rememberUpdatedState(leadingEntries)
    val motionScheme by rememberUpdatedState(MaterialTheme.motionScheme)
    // Below the top, where the date pill floats: as in the list's anchorPlacement.
    val margin = with(LocalDensity.current) { MaterialTheme.spacing.extraExtraLarge.roundToPx() }
    LaunchedEffect(anchor, state) {
        if (entryIndex < 0 || state.movedToAnchor == anchor.id) return@LaunchedEffect
        // The layout that shows the entry: measured at the anchor, or on screen already.
        snapshotFlow { listState.layoutInfo }.first { state.laidOutAnchor == anchor.id }
        listState.moveTo(
            key = key,
            fallbackIndex = currentItems.indexOfFirst { it.key == key }.coerceAtLeast(0) + currentLeading,
            targetOffset = { viewport, size -> position.offset(viewport, size, margin) },
            glide = motionScheme.takeIf { state.glides && position == AnchorPosition.Centre },
        ) { state.movedToAnchor = anchor.id }
        val target = anchor.target
        val shown = target is AnchorTarget.Message && currentItems.any { it.showsMessage(target.messageId) }
        if (shown && target.highlight) state.highlight(target.messageId, motionScheme)
    }
}

/** The index of the entry of the content's anchor among its items: -1 for the newest message, or no unread divider. */
private fun ChatContent.Content.anchorEntryIndex(): Int =
    when (val target = anchor.target) {
        is AnchorTarget.Message -> items.indexOfMessageOrOlder(target.messageId)
        AnchorTarget.Unread -> items.indexOfFirst { it is ChatItem.UnreadDivider }
        AnchorTarget.Newest -> -1
    }

/** Where the target's entry goes: a message in the middle, the unread divider near the top. */
private val AnchorTarget.position: AnchorPosition
    get() = if (this is AnchorTarget.Message) AnchorPosition.Centre else AnchorPosition.NearTop

/** Where an anchor's entry goes in the list's viewport. */
internal enum class AnchorPosition {
    /** Its top a margin below the viewport's top, the entries after it below: the unread divider. */
    NearTop,

    /** In the middle, or its top at the top when it is taller than the viewport: a message jumped to. */
    Centre,
    ;

    /**
     * The offset of an entry [size] high from the start of the reverse layout (its bottom, inside the content padding)
     * in a viewport [viewport] high, with [margin] above it for [NearTop].
     */
    fun offset(
        viewport: Int,
        size: Int,
        margin: Int,
    ): Int =
        when (this) {
            NearTop -> (viewport - margin - size).coerceAtLeast(0)
            Centre -> if (size <= viewport) (viewport - size) / 2 else viewport - size
        }
}

/** Anchor [anchorId]'s entry, at [index] among the list's entries, goes to [position] when the list next measures. */
internal data class AnchorPlacement(
    val anchorId: Int,
    val index: Int,
    val position: AnchorPosition,
)

/**
 * Puts [state]'s pending [AnchorPlacement] into [listState] as the list measures, before it lays out its entries: the
 * entry at its position in the viewport this measure gives, taken as if it had no height, which [FollowListAnchor]
 * corrects once it is measured. [contentPadding] is the list's own.
 */
internal fun Modifier.anchorPlacement(
    state: MessageJumpState,
    listState: LazyListState,
    contentPadding: PaddingValues,
    margin: Dp,
): Modifier = this then AnchorPlacementElement(state, listState, contentPadding, margin)

private data class AnchorPlacementElement(
    val state: MessageJumpState,
    val listState: LazyListState,
    val contentPadding: PaddingValues,
    val margin: Dp,
) : ModifierNodeElement<AnchorPlacementNode>() {
    override fun create() = AnchorPlacementNode(state, listState, contentPadding, margin)

    override fun update(node: AnchorPlacementNode) {
        node.state = state
        node.listState = listState
        node.contentPadding = contentPadding
        node.margin = margin
    }
}

private class AnchorPlacementNode(
    var state: MessageJumpState,
    var listState: LazyListState,
    var contentPadding: PaddingValues,
    var margin: Dp,
) : Modifier.Node(),
    LayoutModifierNode {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        // Read in the measure: a new placement measures the list again.
        val placement = state.placement?.takeIf { it.anchorId != state.appliedPlacement }
        if (placement != null) {
            state.appliedPlacement = placement.anchorId
            val padding = contentPadding.calculateTopPadding() + contentPadding.calculateBottomPadding()
            val viewport = if (constraints.hasBoundedHeight) constraints.maxHeight - padding.roundToPx() else 0
            // A negative offset puts the entry that far from the start: the list measures the entries before it too.
            val offset = placement.position.offset(viewport, size = 0, margin = margin.roundToPx())
            listState.requestScrollToItem(placement.index, -offset)
        }
        val placeable = measurable.measure(constraints)
        if (placement != null) state.laidOutAnchor = placement.anchorId
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/**
 * Hides the list while it waits to move to [anchor] after measuring its items ([FollowListAnchor]): one frame when it
 * first shows them, so it never draws them elsewhere first. Read in the draw phase.
 */
internal fun Modifier.hideUntilAnchored(
    anchor: ListAnchor,
    items: ImmutableList<ChatItem>,
    state: MessageJumpState,
): Modifier {
    val target = anchor.target
    val waits =
        target is AnchorTarget.Message ||
            (target == AnchorTarget.Unread && items.any { it is ChatItem.UnreadDivider })
    return if (waits) graphicsLayer { alpha = if (state.movedToAnchor == anchor.id) 1f else 0f } else this
}

/**
 * The index of the entry showing message [messageId] (an album's item finds the album), else of the newest message
 * older than it, where it would be; else the oldest entry.
 */
private fun List<ChatItem>.indexOfMessageOrOlder(messageId: Long): Int {
    val shown = indexOfFirst { it.showsMessage(messageId) }
    if (shown >= 0) return shown
    val older =
        indexOfFirst { item ->
            when (item) {
                is ChatItem.Bubble -> item.id < messageId
                is ChatItem.Service -> item.id < messageId
                else -> false
            }
        }
    return if (older >= 0) older else lastIndex.coerceAtLeast(0)
}

/** Whether this layout shows the entry at [index] with [key]: one of other items (just replaced) does not. */
private fun LazyListLayoutInfo.showsEntry(
    index: Int,
    key: Any?,
): Boolean = visibleItemsInfo.any { it.index == index && it.key == key }

/** The height of the viewport inside the content padding. */
private val LazyListLayoutInfo.viewport: Int get() = viewportSize.height - beforeContentPadding - afterContentPadding

/**
 * Moves the entry with [key] to the offset [targetOffset] gives for the viewport's height and the entry's: by the
 * distance from where the layout shows it, gliding on [glide]'s default spatial spring if not null, else at once; when
 * the layout does not show it, there at once first ([fallbackIndex]). [onPlaced] runs once the list is where it shows
 * the entry for good, or starts gliding there.
 */
private suspend fun LazyListState.moveTo(
    key: Any?,
    fallbackIndex: Int,
    targetOffset: (viewport: Int, size: Int) -> Int,
    glide: MotionScheme?,
    onPlaced: () -> Unit,
) {
    var entry = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
    if (entry == null) {
        // Measured once there, at the start of the reverse layout: the bottom.
        scrollToItem(fallbackIndex)
        entry = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
    }
    if (entry == null) {
        onPlaced()
        return
    }
    // Offsets run from the start of the layout, the bottom here; scrolling forward moves entries towards it.
    val distance = (entry.offset - targetOffset(layoutInfo.viewport, entry.size)).toFloat()
    if (glide != null) {
        onPlaced()
        animateScrollBy(distance, glide.defaultSpatialSpec())
    } else {
        if (distance != 0f) scrollBy(distance)
        onPlaced()
    }
}

/**
 * The highlight of a jumped-to bubble ([MessageJumpState.highlightOf]): [color] in [shape] behind the row at the
 * highlight's strength, like a selection's state layer. Read in the draw phase; the outline is built once per size.
 */
internal fun Modifier.jumpHighlight(
    state: MessageJumpState,
    bubble: ChatItem.Bubble,
    color: Color,
    shape: Shape,
): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind {
            val strength = state.highlightOf(bubble)
            if (strength > 0f) drawOutline(outline, color, alpha = strength)
        }
    }
