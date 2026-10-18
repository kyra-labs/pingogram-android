package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

/**
 * Where the conversation jumps to when a reply's quote is tapped, or to the message it was opened at
 * (`ChatEffect.ScrollToMessage`): the message to show ([jumpTo]), and the one highlighted once it is there, and how
 * strongly, read in the draw phase.
 *
 * @param highlightedId a message highlighted at full strength from the start, for previews and goldens.
 * @property opensAtMessage the conversation opens at a message (found by search) that a jump will show, so the list
 *   does not scroll to the unread divider when it first shows.
 */
@Stable
internal class MessageJumpState(
    highlightedId: Long? = null,
    val opensAtMessage: Boolean = false,
) {
    /** The message to scroll to next; [JumpToMessages] takes it. */
    var request: JumpRequest? by mutableStateOf(null)
        private set

    private var highlightedId: Long? by mutableStateOf(highlightedId)
    private val strength = Animatable(if (highlightedId != null) 1f else 0f)

    /** Scrolls the list to message [messageId] and highlights it; again for the same message, too. */
    fun jumpTo(messageId: Long) {
        request = JumpRequest(messageId)
    }

    /** Takes [request] off, as it is handled. */
    fun consume(request: JumpRequest) {
        if (this.request === request) this.request = null
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

    /** One tap on a quote: a new instance each time, so the same message can be jumped to again. */
    class JumpRequest(
        val messageId: Long,
    )

    private companion object {
        /** How long the highlight stays at full strength, long enough to find the message on screen. */
        const val HIGHLIGHT_HOLD_MILLIS = 1_000L
    }
}

@Composable
internal fun rememberMessageJumpState(opensAtMessage: Boolean = false): MessageJumpState =
    remember { MessageJumpState(opensAtMessage = opensAtMessage) }

/** The entry shows message [messageId]: a bubble its own or one of its album's, a service message its own. */
internal fun ChatItem.showsMessage(messageId: Long): Boolean =
    when (this) {
        is ChatItem.Bubble -> id == messageId || album?.messageIds?.contains(messageId) == true
        is ChatItem.Service -> id == messageId
        is ChatItem.DateSeparator, is ChatItem.UnreadDivider -> false
    }

/**
 * Takes the jumps of [state] as they come: scrolls [listState] to the message among [items] and highlights it. A new
 * jump replaces one still scrolling or highlighting. Outside the items (`docs/PERFORMANCE.md` §4).
 */
@Composable
internal fun JumpToMessages(
    items: ImmutableList<ChatItem>,
    listState: LazyListState,
    state: MessageJumpState,
    motionScheme: MotionScheme,
) {
    val currentItems by rememberUpdatedState(items)
    val currentMotionScheme by rememberUpdatedState(motionScheme)
    LaunchedEffect(state, listState) {
        snapshotFlow { state.request }.filterNotNull().collectLatest { request ->
            state.consume(request)
            if (listState.scrollToMessage(currentItems, request.messageId, currentMotionScheme)) {
                state.highlight(request.messageId, currentMotionScheme)
            }
        }
    }
}

/**
 * Scrolls to the entry showing message [messageId] among [items] (an album's item finds the album) and centres it
 * ([centreOn]); false if no entry shows it.
 */
internal suspend fun LazyListState.scrollToMessage(
    items: List<ChatItem>,
    messageId: Long,
    motionScheme: MotionScheme,
): Boolean {
    val index = items.indexOfFirst { it.showsMessage(messageId) }
    if (index >= 0) centreOn(index, motionScheme)
    return index >= 0
}

/**
 * Scrolls entry [index] to the middle of the viewport if it fits, else its top to the top: gliding on the default
 * spatial spring when it is on screen already, else at once.
 */
private suspend fun LazyListState.centreOn(
    index: Int,
    motionScheme: MotionScheme,
) {
    val wasVisible = layoutInfo.visibleItemsInfo.any { it.index == index }
    // Measured once there: at the start of the reverse layout, the bottom.
    if (!wasVisible) scrollToItem(index)
    val layout = layoutInfo
    val entry = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewport = layout.viewportSize.height - layout.beforeContentPadding - layout.afterContentPadding
    // Offsets run from the start of the layout, the bottom here; scrolling forward moves entries towards it.
    val target = if (entry.size <= viewport) (viewport - entry.size) / 2 else viewport - entry.size
    val distance = (entry.offset - target).toFloat()
    if (wasVisible) animateScrollBy(distance, motionScheme.defaultSpatialSpec()) else scrollBy(distance)
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
