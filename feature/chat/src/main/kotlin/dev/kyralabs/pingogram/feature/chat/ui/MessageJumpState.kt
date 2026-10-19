package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.platform.LocalDensity
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
 * - **Unread divider**: near the top, so the unread messages follow it downwards.
 * - **Message**: centred, gliding on the default spatial spring when it is on screen, else at once, and highlighted
 *   if the anchor says so.
 *
 * The last two need the items measured. The list's first items are measured in the frame that draws them, so the list
 * hides until it has moved ([hideUntilAnchored]), one frame; later items move at once. Leading entries before the
 * items (the newer pages' indicator) shift every index by [leadingEntries].
 */
@Composable
internal fun FollowListAnchor(
    content: ChatContent.Content,
    listState: LazyListState,
    state: MessageJumpState,
    leadingEntries: Int,
) {
    val anchor = content.anchor
    val target = anchor.target
    val items = content.items
    val dividerIndex = if (target == AnchorTarget.Unread) items.indexOfFirst { it is ChatItem.UnreadDivider } else -1
    val atNewest = target == AnchorTarget.Newest || (target == AnchorTarget.Unread && dividerIndex < 0)
    if (atNewest && state.movedToAnchor != anchor.id) {
        SideEffect {
            listState.requestScrollToItem(0)
            state.movedToAnchor = anchor.id
        }
    }
    val currentItems by rememberUpdatedState(items)
    val currentLeading by rememberUpdatedState(leadingEntries)
    val motionScheme by rememberUpdatedState(MaterialTheme.motionScheme)
    val margin = with(LocalDensity.current) { MaterialTheme.spacing.extraExtraLarge.toPx() }
    LaunchedEffect(anchor, state) {
        if (atNewest || state.movedToAnchor == anchor.id) return@LaunchedEffect
        // Measured once: from the first frame on, the layout knows its viewport and the items.
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        when (target) {
            is AnchorTarget.Message -> {
                val index = currentItems.indexOfMessageOrOlder(target.messageId)
                val key = currentItems.getOrNull(index)?.key
                listState.centreOn(index + currentLeading, key, motionScheme) { state.movedToAnchor = anchor.id }
                if (target.highlight && currentItems.any { it.showsMessage(target.messageId) }) {
                    state.highlight(target.messageId, motionScheme)
                }
            }

            else -> {
                listState.showNearTop(dividerIndex + currentLeading, margin)
                state.movedToAnchor = anchor.id
            }
        }
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

/**
 * Scrolls entry [index] to near the top of the viewport, [margin] below it (where the date pill floats), so the
 * entries after it follow downwards: measured at once at the bottom, then moved up by the viewport's height less its
 * own and the margin.
 */
private suspend fun LazyListState.showNearTop(
    index: Int,
    margin: Float,
) {
    scrollToItem(index)
    val layout = layoutInfo
    val entry = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewport = layout.viewportSize.height - layout.beforeContentPadding - layout.afterContentPadding
    scrollBy(-(viewport - entry.size - margin).coerceAtLeast(0f))
}

/**
 * Scrolls entry [index] (with [key]) to the middle of the viewport if it fits, else its top to the top: gliding on the
 * default spatial spring when the layout shows it already, else at once. [onPlaced] runs once the list is where it
 * shows the entry for good, or starts gliding there. A layout of other items (just replaced) does not show it.
 */
private suspend fun LazyListState.centreOn(
    index: Int,
    key: Any?,
    motionScheme: MotionScheme,
    onPlaced: () -> Unit,
) {
    val wasVisible = layoutInfo.visibleItemsInfo.any { it.index == index && it.key == key }
    // Measured once there: at the start of the reverse layout, the bottom.
    if (!wasVisible) scrollToItem(index)
    val layout = layoutInfo
    val entry = layout.visibleItemsInfo.firstOrNull { it.index == index }
    if (entry == null) {
        onPlaced()
        return
    }
    val viewport = layout.viewportSize.height - layout.beforeContentPadding - layout.afterContentPadding
    // Offsets run from the start of the layout, the bottom here; scrolling forward moves entries towards it.
    val target = if (entry.size <= viewport) (viewport - entry.size) / 2 else viewport - entry.size
    val distance = (entry.offset - target).toFloat()
    if (wasVisible) {
        onPlaced()
        animateScrollBy(distance, motionScheme.defaultSpatialSpec())
    } else {
        scrollBy(distance)
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
