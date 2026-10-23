package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.toSize

/**
 * Calls [onLongPress] once the pointer has stayed down, without moving beyond the touch slop, for the long-press
 * timeout, with where this element is in the window (unclipped, so a row half under the top bar keeps its full
 * height), and then consumes the rest of the gesture. It watches the pointer before the content does (the initial
 * pass), so it works over buttons, media and links, which then see the release consumed and take no tap; a scroll or a
 * swipe that starts first cancels it. A mouse's secondary button (a right click) calls [onSecondaryClick] at once, the
 * same way, and the content sees none of it (`docs/DESIGN_SYSTEM.md` §9 "Keyboard & mouse"). A [Modifier.Node], so
 * the element's place is read only when a long press or a right click needs it, never while the list scrolls.
 */
internal fun Modifier.onLongPress(
    onLongPress: (Rect) -> Unit,
    onSecondaryClick: (Rect) -> Unit = onLongPress,
): Modifier = this then LongPressElement(onLongPress, onSecondaryClick)

private class LongPressElement(
    private val onLongPress: (Rect) -> Unit,
    private val onSecondaryClick: (Rect) -> Unit,
) : ModifierNodeElement<LongPressNode>() {
    override fun create() = LongPressNode(onLongPress, onSecondaryClick)

    override fun update(node: LongPressNode) {
        node.onLongPress = onLongPress
        node.onSecondaryClick = onSecondaryClick
    }

    override fun equals(other: Any?): Boolean =
        other is LongPressElement &&
            other.onLongPress === onLongPress &&
            other.onSecondaryClick === onSecondaryClick

    override fun hashCode(): Int = 31 * onLongPress.hashCode() + onSecondaryClick.hashCode()
}

private class LongPressNode(
    var onLongPress: (Rect) -> Unit,
    var onSecondaryClick: (Rect) -> Unit,
) : DelegatingNode() {
    init {
        delegate(
            SuspendingPointerInputModifierNode {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (currentEvent.buttons.isSecondaryPressed) {
                        down.consume()
                        onSecondaryClick(windowBounds())
                        consumeUntilUp()
                    } else if (awaitLongPress(down)) {
                        onLongPress(windowBounds())
                        consumeUntilUp()
                    }
                }
            },
        )
    }

    private fun windowBounds(): Rect {
        val coordinates = requireLayoutCoordinates()
        return Rect(coordinates.positionInWindow(), coordinates.size.toSize())
    }
}

/**
 * A bubble row's gestures: in selection mode ([selecting]) its taps and long presses [toggle] it; otherwise a long
 * press opens its actions ([showActions]) and, where it has a [swipe], a swipe toward the end replies (mirrored in
 * right-to-left layouts, [isRtl]).
 */
internal fun Modifier.bubbleGestures(
    selecting: Boolean,
    toggle: () -> Unit,
    showActions: ActionsOpener,
    swipe: SwipeToReplyState?,
    isRtl: Boolean,
): Modifier =
    when {
        selecting -> onSelectionTap(toggle)
        swipe != null -> onLongPress(showActions, showActions.onSecondaryClick).swipeToReply(swipe, isRtl)
        else -> onLongPress(showActions, showActions.onSecondaryClick)
    }

/**
 * Opens the actions of message [messageId] with a long-press haptic ([ChatEvent.ShowMessageActions]). Called with the
 * row's place in the window by a long press, it lifts the message there for its floating menu where [lift] allows
 * ([MessageLiftState.lift]); called without one (screen readers' long click), the actions open in their sheet. A
 * mouse's secondary button ([onSecondaryClick]) and a key ([open] with the focus to give back) lift it the same way,
 * and the menu takes the focus, so that Esc closes it.
 */
@Stable
internal class ActionsOpener(
    private val messageId: Long,
    private val onEvent: (ChatEvent) -> Unit,
    private val haptics: HapticFeedback,
    private val lift: MessageLiftState,
) : (Rect?) -> Unit {
    /** A right click: lifted as by a long press, the menu taking the focus. */
    val onSecondaryClick: (Rect) -> Unit = { bounds -> open(bounds, takesFocus = true) }

    override fun invoke(bounds: Rect?) = open(bounds, takesFocus = false)

    /** Opens the actions from [bounds]; the menu takes the focus if [takesFocus] and gives it back by [returnFocus]. */
    fun open(
        bounds: Rect?,
        takesFocus: Boolean,
        returnFocus: (() -> Unit)? = null,
    ) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        lift.lift(messageId, bounds, takesFocus, returnFocus)
        onEvent(ChatEvent.ShowMessageActions(messageId))
    }
}

/** The [ActionsOpener] of message [messageId], for its row. */
@Composable
internal fun rememberActionsOpener(
    messageId: Long,
    onEvent: (ChatEvent) -> Unit,
    lift: MessageLiftState,
): ActionsOpener {
    val haptics = LocalHapticFeedback.current
    return remember(messageId, onEvent, haptics, lift) { ActionsOpener(messageId, onEvent, haptics, lift) }
}

/**
 * Selection mode's taps: calls [onToggle] when the pointer is released within the touch slop, or once it has stayed
 * down for the long-press timeout (then consuming the rest of the gesture), so a tap and a long press both select or
 * deselect. It takes the press before the content does (the initial pass): buttons, media and links inside see it
 * consumed and do nothing. A scroll that starts first is left to the list.
 */
internal fun Modifier.onSelectionTap(onToggle: () -> Unit): Modifier =
    pointerInput(onToggle) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Consumed, so the content's buttons and links do not start a press; the list still scrolls.
            down.consume()
            val released =
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var tap: Boolean? = null
                    while (tap == null) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        tap =
                            when {
                                change == null || event.changes.size > 1 -> false
                                (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> false
                                !change.pressed -> true.also { change.consume() }
                                else -> null
                            }
                    }
                    tap
                }
            // Released within the slop (true), or held for the long-press timeout (null); false is a scroll.
            if (released != false) {
                onToggle()
                if (released == null) consumeUntilUp()
            }
        }
    }

/**
 * Whether the pointer that went [down] stays down, alone and within the touch slop, for the long-press timeout.
 * Watches the initial pass and consumes nothing.
 */
private suspend fun AwaitPointerEventScope.awaitLongPress(down: PointerInputChange): Boolean {
    val ended =
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                val holds =
                    change != null &&
                        change.pressed &&
                        event.changes.size == 1 &&
                        (change.position - down.position).getDistance() <= viewConfiguration.touchSlop
            } while (holds)
        }
    // No end within the timeout: a long press.
    return ended == null
}

/** Consumes every change in the initial pass until all pointers are up, so the content takes no tap. */
private suspend fun AwaitPointerEventScope.consumeUntilUp() {
    do {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach { it.consume() }
    } while (event.changes.any { it.pressed })
}
