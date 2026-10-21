package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
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
 * swipe that starts first cancels it. A [Modifier.Node], so the element's place is read only when a long press needs
 * it, never while the list scrolls.
 */
internal fun Modifier.onLongPress(onLongPress: (Rect) -> Unit): Modifier = this then LongPressElement(onLongPress)

private class LongPressElement(
    private val onLongPress: (Rect) -> Unit,
) : ModifierNodeElement<LongPressNode>() {
    override fun create() = LongPressNode(onLongPress)

    override fun update(node: LongPressNode) {
        node.onLongPress = onLongPress
    }

    override fun equals(other: Any?): Boolean = other is LongPressElement && other.onLongPress === onLongPress

    override fun hashCode(): Int = onLongPress.hashCode()
}

private class LongPressNode(
    var onLongPress: (Rect) -> Unit,
) : DelegatingNode() {
    init {
        delegate(
            SuspendingPointerInputModifierNode {
                awaitEachGesture {
                    if (awaitLongPress()) {
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
    showActions: (Rect?) -> Unit,
    swipe: SwipeToReplyState?,
    isRtl: Boolean,
): Modifier =
    when {
        selecting -> onSelectionTap(toggle)
        swipe != null -> onLongPress(showActions).swipeToReply(swipe, isRtl)
        else -> onLongPress(showActions)
    }

/**
 * Opens the actions of message [messageId] with a long-press haptic ([ChatEvent.ShowMessageActions]). Called with the
 * row's place in the window by a long press, it lifts the message there for its floating menu where [lift] allows
 * ([MessageLiftState.lift]); called without one (screen readers' long click), the actions open in their sheet.
 */
@Composable
internal fun rememberActionsOpener(
    messageId: Long,
    onEvent: (ChatEvent) -> Unit,
    lift: MessageLiftState,
): (Rect?) -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(messageId, onEvent, haptics, lift) {
        { bounds ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            lift.lift(messageId, bounds)
            onEvent(ChatEvent.ShowMessageActions(messageId))
        }
    }
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
 * Whether the pointer that goes down next stays down, alone and within the touch slop, for the long-press timeout.
 * Watches the initial pass and consumes nothing.
 */
private suspend fun AwaitPointerEventScope.awaitLongPress(): Boolean {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
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
