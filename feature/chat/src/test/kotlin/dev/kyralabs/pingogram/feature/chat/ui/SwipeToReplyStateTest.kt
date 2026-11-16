package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A bubble let go after a swipe to reply springs back from the speed the finger gave it (plan A-4, M-039). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SwipeToReplyStateTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var replies = 0
    private lateinit var state: SwipeToReplyState

    private fun setState() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                state = SwipeToReplyState(rememberSwipeReplyStyle()) { replies++ }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
    }

    private fun swipe(
        distance: Float,
        velocity: Float,
    ): Float =
        composeRule.runOnIdle {
            state.draggable.dispatchRawDelta(distance)
            val released = state.offset
            runBlocking { state.onStopped(this, velocity) }
            released
        }

    private fun frame() {
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    @Test
    fun `flicked on toward the end, the bubble carries on before it springs back`() {
        setState()

        val released = swipe(SHORT, velocity = FLICK)
        // An animation's first frame only marks its start.
        repeat(2) { frame() }

        assertTrue("${state.offset} after $released", state.offset > released)
    }

    @Test
    fun `let go at rest, it springs straight back to its place`() {
        setState()

        val released = swipe(SHORT, velocity = 0f)
        repeat(2) { frame() }
        val first = state.offset
        repeat(SETTLE_FRAMES) { frame() }

        assertTrue("$first after $released", first < released)
        assertEquals(0f, state.offset, 0.5f)
        assertEquals(0, replies)
    }

    private companion object {
        const val SHORT = 40f
        const val FLICK = 3000f
        const val SETTLE_FRAMES = 60
    }
}
