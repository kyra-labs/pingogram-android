package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

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
