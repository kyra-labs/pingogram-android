package dev.kyralabs.pingogram.core.ui.gesture

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.ui.component.ComponentActivityRegistration
import dev.kyralabs.pingogram.core.ui.component.TestDurationScale
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [SettlingOffset] (`docs/plans/motion-expressive-plan.md` §3.4): a drag takes over from a running settle, a settle
 * starts at the release's velocity, and with animations off it is at its target in the next frame.
 */
@RunWith(RobolectricTestRunner::class)
class SettlingOffsetTest {
    private val durationScale = TestDurationScale()

    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>(effectContext = durationScale)

    private lateinit var scope: CoroutineScope
    private lateinit var motionScheme: MotionScheme

    @Test
    fun `a drag during a settle cancels it, and its end does not run`() {
        setUp()
        val offset = SettlingOffset(START)
        var settled = false

        offset.settle(scope, 0f, motionScheme.defaultSpatialSpec(), onSettled = { settled = true })
        frames(2)
        assertTrue(offset.isSettling)
        offset.dragBy(DRAG)
        val held = offset.value
        frames(SETTLE_FRAMES)

        assertFalse(settled)
        assertFalse(offset.isSettling)
        assertEquals(held, offset.value)
    }

    @Test
    fun `a settle runs to its target over several frames, then its end runs`() {
        setUp()
        val offset = SettlingOffset(START)
        var settled = false

        offset.settle(scope, 0f, motionScheme.defaultSpatialSpec(), onSettled = { settled = true })
        frames(2)
        val midway = offset.value
        frames(SETTLE_FRAMES)

        assertTrue("midway at $midway", midway > 0f && midway < START)
        assertEquals(0f, offset.value)
        assertTrue(settled)
    }

    @Test
    fun `a settle starts at the velocity given, even away from its target`() {
        setUp()
        val offset = SettlingOffset(START)

        // Flung on past START while the target is behind it: the first frames still move on.
        offset.settle(scope, 0f, motionScheme.defaultSpatialSpec(), velocity = FLING)
        frames(1)

        assertTrue("first frame at ${offset.value}", offset.value > START)
    }

    @Test
    fun `with animations off a settle is at its target in the next frame`() {
        durationScale.scaleFactor = 0f
        setUp()
        val offset = SettlingOffset(START)
        var settled = false

        offset.settle(scope, 0f, motionScheme.defaultSpatialSpec(), onSettled = { settled = true })
        frames(2)

        assertEquals(0f, offset.value)
        assertTrue(settled)
    }

    @Test
    fun `a drag stays within its range`() {
        val offset = SettlingOffset()

        offset.dragBy(START * 2, range = 0f..START)

        assertEquals(START, offset.value)
    }

    private fun setUp() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                scope = rememberCoroutineScope()
                motionScheme = MaterialTheme.motionScheme
            }
        }
        frames(1)
    }

    private fun frames(count: Int) {
        repeat(count) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()
    }

    private companion object {
        const val START = 200f
        const val DRAG = 10f
        const val FLING = 20_000f
        const val SETTLE_FRAMES = 120
    }
}
