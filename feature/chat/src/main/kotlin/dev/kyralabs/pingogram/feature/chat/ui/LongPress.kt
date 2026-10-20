package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Calls [onLongPress] once the pointer has stayed down, without moving beyond the touch slop, for the long-press
 * timeout, and then consumes the rest of the gesture. It watches the pointer before the content does (the initial
 * pass), so it works over buttons, media and links, which then see the release consumed and take no tap; a scroll that
 * starts first cancels it.
 */
internal fun Modifier.onLongPress(onLongPress: () -> Unit): Modifier =
    pointerInput(onLongPress) {
        awaitEachGesture {
            if (awaitLongPress()) {
                onLongPress()
                consumeUntilUp()
            }
        }
    }

/** Opens the actions of message [messageId] with a long-press haptic ([ChatEvent.ShowMessageActions]). */
@Composable
internal fun rememberActionsOpener(
    messageId: Long,
    onEvent: (ChatEvent) -> Unit,
): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(messageId, onEvent, haptics) {
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
