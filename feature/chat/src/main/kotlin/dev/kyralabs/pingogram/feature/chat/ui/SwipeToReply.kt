package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.theme.sizes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What every bubble's swipe to reply shares (`docs/DESIGN_SYSTEM.md` §5, §6 "Message actions"; Google Messages'
 * swipe to reply): how far a swipe goes to reply ([distance], `sizes.swipeReplyDistance`), the reply [icon] in a
 * circle of [containerColor] revealed behind the bubble, the haptics, and the motion scheme's springs: the bubble
 * springs back on [settleSpec] (default spatial), the icon pops on [popSpec] (fast spatial) as the swipe passes the
 * distance. Built once for the list.
 */
@Suppress("LongParameterList") // One holder for what every bubble's swipe shares, so the bubbles take none of it.
@Stable
internal class SwipeReplyStyle(
    val distance: Float,
    val circle: Float,
    val iconSize: Float,
    val icon: Painter,
    val containerColor: Color,
    val iconFilter: ColorFilter,
    val haptics: HapticFeedback,
    val scope: CoroutineScope,
    val settleSpec: FiniteAnimationSpec<Float>,
    val popSpec: FiniteAnimationSpec<Float>,
)

/** The [SwipeReplyStyle] of the conversation's bubbles: the icon of a small icon button in its tonal colors. */
@Composable
internal fun rememberSwipeReplyStyle(): SwipeReplyStyle {
    val density = LocalDensity.current
    val distance = MaterialTheme.sizes.swipeReplyDistance
    val circle = IconButtonDefaults.smallContainerSize().width
    val iconSize = IconButtonDefaults.smallIconSize
    val icon = painterResource(UiR.drawable.ic_reply)
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Not keyed on the icon: painterResource hands out a new painter of the same vector as the screen recomposes,
    // which would recompose every bubble; the first one draws the same.
    return remember(density, distance, circle, iconSize, scheme, motion, haptics, scope) {
        with(density) {
            SwipeReplyStyle(
                distance = distance.toPx(),
                circle = circle.toPx(),
                iconSize = iconSize.toPx(),
                icon = icon,
                containerColor = scheme.surfaceContainerHighest,
                iconFilter = ColorFilter.tint(scheme.onSurfaceVariant),
                haptics = haptics,
                scope = scope,
                settleSpec = motion.defaultSpatialSpec(),
                popSpec = motion.fastSpatialSpec(),
            )
        }
    }
}

/**
 * A bubble's swipe to reply: how far it has moved toward the end ([offset], in pixels), which the row reads in the
 * layout phase and its icon in the draw phase, so following the finger never recomposes (`docs/PERFORMANCE.md` §4).
 *
 * - Past the [SwipeReplyStyle.distance] it is armed: a `TextHandleMove` haptic, and the icon pops to its full size on
 *   the fast spatial spring; back under it, the icon shrinks again. Beyond it the bubble follows the finger at a
 *   fraction of its speed.
 * - Released armed, it replies ([onReply], the same event as the actions' "Reply"); armed or not, the bubble springs
 *   back on the default spatial spring. A new swipe takes the bubble where the spring has it.
 */
@Stable
internal class SwipeToReplyState(
    private val style: SwipeReplyStyle,
    private val onReply: () -> Unit,
) {
    /** How far the bubble is from its place, toward the end. */
    var offset by mutableFloatStateOf(0f)
        private set

    private var iconScale by mutableFloatStateOf(UNARMED_ICON_SCALE)
    private var dragged = 0f
    private var armed = false
    private var popJob: Job? = null
    private var settleJob: Job? = null

    val draggable = DraggableState(::drag)

    val onStarted: suspend CoroutineScope.(Offset) -> Unit = {
        settleJob?.cancel()
        val distance = style.distance
        dragged = if (offset <= distance) offset else distance + (offset - distance) / BEYOND_FACTOR
    }

    val onStopped: suspend CoroutineScope.(Float) -> Unit = { release() }

    private fun drag(delta: Float) {
        val distance = style.distance
        dragged = (dragged + delta).coerceIn(0f, distance * MAX_DRAG_FACTOR)
        offset = if (dragged <= distance) dragged else distance + (dragged - distance) * BEYOND_FACTOR
        arm(dragged >= distance)
    }

    private fun release() {
        if (armed) onReply()
        arm(false)
        dragged = 0f
        val from = offset
        settleJob =
            style.scope.launch {
                animate(from, 0f, animationSpec = style.settleSpec) { value, _ -> offset = value }
            }
    }

    private fun arm(now: Boolean) {
        if (now == armed) return
        armed = now
        if (now) style.haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        popJob?.cancel()
        val from = iconScale
        val to = if (now) 1f else UNARMED_ICON_SCALE
        popJob =
            style.scope.launch {
                animate(from, to, animationSpec = style.popSpec) { value, _ -> iconScale = value }
            }
    }

    /**
     * The reply icon in its circle, at the start of the row in the room the bubble leaves, growing and fading in with
     * the swipe until it is armed.
     */
    fun DrawScope.drawIcon() {
        val shown = offset
        if (shown <= 0f) return
        val progress = (shown / style.distance).coerceIn(0f, 1f)
        val scale = progress * iconScale
        val fromStart = minOf(shown, style.distance) / 2
        val center = Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - fromStart else fromStart, center.y)
        drawCircle(style.containerColor, radius = style.circle / 2 * scale, center = center, alpha = progress)
        val icon = style.iconSize * scale
        translate(center.x - icon / 2, center.y - icon / 2) {
            with(style.icon) { draw(Size(icon, icon), alpha = progress, colorFilter = style.iconFilter) }
        }
    }

    private companion object {
        /** How big the icon is until the swipe is armed, of its full size. */
        const val UNARMED_ICON_SCALE = 0.7f

        /** How fast the bubble follows the finger beyond the distance. */
        const val BEYOND_FACTOR = 0.3f

        /** How far a swipe is followed, in distances. */
        const val MAX_DRAG_FACTOR = 3f
    }
}

/** The swipe to reply of message [messageId], which sends [ChatEvent.Reply] once released past its distance. */
@Composable
internal fun rememberSwipeToReply(
    messageId: Long,
    onEvent: (ChatEvent) -> Unit,
    style: SwipeReplyStyle,
): SwipeToReplyState =
    remember(messageId, onEvent, style) { SwipeToReplyState(style) { onEvent(ChatEvent.Reply(messageId)) } }

/**
 * Swipes this row toward the end to reply ([state]): a horizontal drag, so the list's vertical scroll and a long press
 * keep theirs (whichever passes its touch slop first wins), mirrored in right-to-left layouts ([isRtl]); the reply icon
 * is drawn behind the row.
 */
internal fun Modifier.swipeToReply(
    state: SwipeToReplyState,
    isRtl: Boolean,
): Modifier =
    draggable(
        state = state.draggable,
        orientation = Orientation.Horizontal,
        onDragStarted = state.onStarted,
        onDragStopped = state.onStopped,
        reverseDirection = isRtl,
    ).drawBehind { with(state) { drawIcon() } }

/** Moves this content by [state]'s offset toward the end, in the placement phase (mirrored in right-to-left). */
internal fun Modifier.swipeOffset(state: SwipeToReplyState): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.placeRelative(state.offset.roundToInt(), 0) }
    }
