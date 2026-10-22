package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.animation.core.snap
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The viewer closes once ([DismissState.dismiss]): its exit plays while it is still on screen and can be swiped. */
class DismissStateTest {
    private val scope = TestScope()

    @Test
    fun `a swipe past the threshold closes the viewer`() {
        val state = DismissState()
        var closed = 0

        state.draggableState.dispatchRawDelta(HEIGHT * FAR)
        state.onDragStopped(velocity = 0f, height = HEIGHT, spec = snap(), scope = scope) { closed++ }

        assertEquals(1, closed)
        assertTrue(state.isDismissed)
    }

    @Test
    fun `a second swipe while the viewer leaves closes nothing more`() {
        val state = DismissState()
        var closed = 0
        state.draggableState.dispatchRawDelta(HEIGHT * FAR)

        state.onDragStopped(velocity = 0f, height = HEIGHT, spec = snap(), scope = scope) { closed++ }
        state.onDragStopped(velocity = HEIGHT * FAST, height = HEIGHT, spec = snap(), scope = scope) { closed++ }

        assertEquals(1, closed)
    }

    @Test
    fun `the back button after a swipe closes nothing more`() {
        val state = DismissState()
        var closed = 0
        state.draggableState.dispatchRawDelta(HEIGHT * FAR)
        state.onDragStopped(velocity = 0f, height = HEIGHT, spec = snap(), scope = scope) { closed++ }

        state.dismiss { closed++ }

        assertEquals(1, closed)
    }

    @Test
    fun `a short swipe closes nothing`() {
        val state = DismissState()
        var closed = 0
        state.draggableState.dispatchRawDelta(HEIGHT * SHORT)

        state.onDragStopped(velocity = 0f, height = HEIGHT, spec = snap(), scope = scope) { closed++ }

        assertEquals(0, closed)
        assertEquals(false, state.isDismissed)
    }

    private companion object {
        const val HEIGHT = 2_000f
        const val FAR = 0.5f
        const val SHORT = 0.05f
        const val FAST = 3f
    }
}
