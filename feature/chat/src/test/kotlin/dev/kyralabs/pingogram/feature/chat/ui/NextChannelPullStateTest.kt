package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Velocity
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.preview.NextChannelPreviewData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The pull to the next channel once let go (`docs/DESIGN_SYSTEM.md` §6 "Next unread channel", plan A-4): it settles
 * back on its own, from the fling's speed, a new pull takes the list where the settle has it, and the haptic marks the
 * threshold both ways.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class NextChannelPullStateTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val haptics = mutableListOf<HapticFeedbackType>()
    private val opened = mutableListOf<Long>()
    private lateinit var state: NextChannelPullState

    private fun setState() {
        val feedback =
            object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    haptics += hapticFeedbackType
                }
            }
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                val scope = rememberCoroutineScope()
                val settle = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
                state =
                    NextChannelPullState(NextChannelPreviewData.next, THRESHOLD, feedback, settle, scope, {
                        opened += it
                    })
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
    }

    /** Drags the list up by [distance] pixels past its newest post: what the list leaves of the drag. */
    private fun pull(distance: Float) =
        composeRule.runOnIdle {
            state.connection.onPostScroll(Offset.Zero, Offset(0f, -distance), NestedScrollSource.UserInput)
        }

    private fun release(velocity: Float = 0f) =
        composeRule.runOnIdle { runBlocking { state.connection.onPreFling(Velocity(0f, velocity)) } }

    private fun frames(count: Int) =
        repeat(count) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }

    @Test
    fun `let go, the pull settles back by itself over several frames`() {
        setState()
        pull(THRESHOLD)

        release()
        val settling = List(SETTLE_FRAMES) { frames(1).let { state.pull } }

        assertTrue("$settling", settling.distinct().size > MOVING_FRAMES)
        assertEquals(0f, settling.last(), 0.5f)
    }

    @Test
    fun `a pull during the settle takes the list where it is, and the settle stops`() {
        setState()
        pull(THRESHOLD)
        release()
        frames(2)
        val caught = state.pull

        pull(STEP)
        frames(SETTLE_FRAMES)

        assertTrue("caught at $caught", caught > 0f)
        assertEquals(caught + STEP * NextChannelPullState.RESISTANCE, state.pull, 0.5f)
    }

    @Test
    fun `an upward fling carries the settle on upward first`() {
        setState()
        pull(THRESHOLD)
        val before = state.pull

        release(velocity = -FLING)
        // An animation's first frame only marks its start.
        frames(2)

        assertTrue("${state.pull} after $before", state.pull > before)
    }

    @Test
    fun `the haptic marks the threshold as the finger crosses it, and as it goes back`() {
        setState()

        pull(THRESHOLD * 2 + STEP)
        composeRule.runOnIdle {
            state.connection.onPreScroll(Offset(0f, THRESHOLD), NestedScrollSource.UserInput)
        }

        assertEquals(listOf(HapticFeedbackType.TextHandleMove, HapticFeedbackType.TextHandleMove), haptics)
        assertEquals(emptyList<Long>(), opened)
    }

    private companion object {
        const val THRESHOLD = 200f
        const val STEP = 20f
        const val FLING = 4000f
        const val SETTLE_FRAMES = 60
        const val MOVING_FRAMES = 4
    }
}
