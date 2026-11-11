package dev.kyralabs.pingogram.feature.calls.ui

import android.content.ComponentName
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.calls.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The incoming call's answer slider (`docs/DESIGN_SYSTEM.md` §6 "Call screen", motion plan B-1): a flick toward
 * "Answer" answers, the answer comes as the finger lifts, a drag takes the handle from its spring back, nothing springs
 * while power saving holds the call screens still, and with a screen reader exploring by touch the buttons show
 * instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class AnswerSliderTest {
    @get:Rule(order = 0)
    val activityRegistration =
        object : ExternalResource() {
            override fun before() {
                val application = RuntimeEnvironment.getApplication()
                shadowOf(application.packageManager)
                    .addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
            }
        }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<CallEvent>()

    @Test
    fun `a quick flick toward Answer answers`() {
        setScreen()

        composeRule.onNodeWithTag(ANSWER_SLIDER_HANDLE, useUnmergedTree = true).performTouchInput {
            swipeWithVelocity(center, center + Offset(width * 0.6f, 0f), endVelocity = FLICK_VELOCITY)
        }
        composeRule.waitForIdle()

        assertEquals(listOf(CallEvent.Answer), events)
    }

    @Test
    fun `releasing past the threshold answers as the finger lifts, before the handle has finished`() {
        setScreen()
        composeRule.mainClock.autoAdvance = false
        val travel = travel()

        composeRule.onNodeWithTag(ANSWER_SLIDER_HANDLE, useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(travel * 0.8f, 0f), delayMillis = SLOW_MOVE_MILLIS)
            up()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(CallEvent.Answer), events)
    }

    @Test
    fun `a short slide springs back, and a touch during the spring keeps the handle under the finger`() {
        setScreen()
        val start = handleLeft()
        composeRule.mainClock.autoAdvance = false
        val travel = travel()

        composeRule.onNodeWithTag(ANSWER_SLIDER_HANDLE, useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(travel * 0.4f, 0f), delayMillis = SLOW_MOVE_MILLIS)
            up()
        }
        repeat(SPRING_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()
        val caught = handleLeft()
        assertTrue("the handle springs back: $start, then $caught", caught > start)

        composeRule.onNodeWithTag(ANSWER_SLIDER_HANDLE, useUnmergedTree = true).performTouchInput { down(center) }
        repeat(HELD_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()

        assertEquals(caught.value, handleLeft().value, POSITION_TOLERANCE)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `while power saving holds the call screens still, a short slide is back at once`() {
        setScreen(still = true)
        val start = handleLeft()
        composeRule.mainClock.autoAdvance = false
        val travel = travel()

        composeRule.onNodeWithTag(ANSWER_SLIDER_HANDLE, useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(travel * 0.4f, 0f), delayMillis = SLOW_MOVE_MILLIS)
            up()
        }
        repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()

        assertEquals(start.value, handleLeft().value, POSITION_TOLERANCE)
    }

    @Test
    fun `with a screen reader exploring by touch the buttons show instead of the slider`() {
        val manager = RuntimeEnvironment.getApplication().getSystemService(AccessibilityManager::class.java)
        shadowOf(manager).setEnabled(true)
        shadowOf(manager).setTouchExplorationEnabled(true)
        setScreen()

        composeRule.onNodeWithContentDescription(slideDescription()).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.call_answer)).assertExists()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.call_decline)).assertExists()
    }

    private fun handleLeft(): Dp =
        composeRule.onNodeWithTag(ANSWER_SLIDER_HANDLE, useUnmergedTree = true).getBoundsInRoot().left

    /** How far, in pixels, the handle can go from the middle toward either side. */
    private fun travel(): Float {
        val slider = composeRule.onNodeWithContentDescription(slideDescription()).getBoundsInRoot()
        val handle = composeRule.onNodeWithTag(ANSWER_SLIDER_HANDLE, useUnmergedTree = true).getBoundsInRoot()
        val widthDp = (slider.right - slider.left) - (handle.right - handle.left)
        return with(composeRule.density) { (widthDp / 2).toPx() }
    }

    private fun slideDescription(): String = composeRule.activity.getString(R.string.call_slide_description)

    private fun setScreen(still: Boolean = false) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalStillCallAnimations provides still) {
                    CallScreen(CallPreviewStates.incoming, { events += it }, {}, {}, {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    private companion object {
        /** Pixels per second: a flick, far faster than a slide. */
        const val FLICK_VELOCITY = 8_000f
        const val SLOW_MOVE_MILLIS = 600L
        const val SPRING_FRAMES = 3
        const val HELD_FRAMES = 10
        const val POSITION_TOLERANCE = 1f
    }
}
