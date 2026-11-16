package dev.kyralabs.pingogram.feature.media.ui.zoom

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Velocity
import dev.kyralabs.pingogram.feature.media.ui.ComponentActivityRegistration
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A pan released while zoomed (M-074 of the motion audit): the photo glides on with the finger's velocity over several
 * frames, within its bounds, and with animations off (reduced motion) it is where it would stop at once.
 */
@RunWith(RobolectricTestRunner::class)
class ZoomFlingTest {
    private val durationScale =
        object : MotionDurationScale {
            override var scaleFactor = 1f
        }

    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>(effectContext = durationScale)

    private lateinit var scope: CoroutineScope
    private lateinit var decay: DecayAnimationSpec<Offset>

    @Test
    fun `a released pan glides on over several frames, then stops`() {
        val state = zoomedIn()

        state.fling(FLING, decay, scope)
        frames(2)
        val early = state.zoom.pan.x
        frames(GLIDE_FRAMES)
        val stopped = state.zoom.pan.x
        frames(GLIDE_FRAMES)

        assertTrue("early $early", early < 0f)
        assertTrue("early $early, stopped $stopped", stopped < early)
        assertEquals(stopped, state.zoom.pan.x, 0f)
    }

    @Test
    fun `with animations off a released pan is where it stops at once`() {
        durationScale.scaleFactor = 0f
        val state = zoomedIn()

        state.fling(FLING, decay, scope)
        frames(2)
        val atOnce = state.zoom.pan.x
        frames(GLIDE_FRAMES)

        assertTrue("at $atOnce", atOnce < 0f)
        assertEquals(atOnce, state.zoom.pan.x, 0f)
    }

    /** A zoom state of [BOUNDS] zoomed in three times around the center. */
    private fun zoomedIn(): ZoomState {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            scope = rememberCoroutineScope()
            decay = rememberSplineBasedDecay()
        }
        frames(1)
        return ZoomState().apply {
            bounds = BOUNDS
            transform(centroid = Offset.Zero, scaleChange = ZOOM, panChange = Offset.Zero)
        }
    }

    private fun frames(count: Int) {
        // Applies what the test changed first, so the frame sees it.
        composeRule.waitForIdle()
        repeat(count) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()
    }

    private companion object {
        /** A 1000 × 2000 page with a 4:3 photo fitted across its width. */
        val BOUNDS = ZoomBounds(container = Size(1000f, 2000f), content = Size(1000f, 750f))
        const val ZOOM = 3f

        /** A flick toward the start, slow enough that the photo stops before its edge. */
        val FLING = Velocity(-600f, 0f)

        /** Enough frames for the decay to end. */
        const val GLIDE_FRAMES = 120
    }
}
