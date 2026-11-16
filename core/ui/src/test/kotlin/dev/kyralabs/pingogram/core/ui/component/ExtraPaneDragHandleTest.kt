package dev.kyralabs.pingogram.core.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * The extra pane's drag handle (`docs/plans/motion-expressive-plan.md` S-3, M-001): released, the pane settles on a
 * width over several frames; grabbed again within the settle, it stays where the finger puts it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1600dp-h1000dp-mdpi")
class ExtraPaneDragHandleTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var state: ExtraPaneWidth

    @Test
    fun `a released handle settles on a width over several frames`() {
        setUp()
        drag(-DRAG, release = true)
        val released = state.width.value

        val frames = composeRule.framesUntil { !state.offset.isSettling }

        assertTrue("settled in $frames frames from $released", frames > 2)
        assertTrue("settled at ${state.width}", onAnchor())
    }

    @Test
    fun `grabbing the handle within the settle leaves the pane where the finger puts it`() {
        setUp()
        drag(-DRAG, release = true)
        composeRule.framesUntil(limit = 2) { false }

        drag(SMALL_DRAG, release = false)
        val held = state.width
        composeRule.framesUntil(limit = SETTLE_FRAMES) { false }

        assertEquals(held, state.width)
        assertFalse("caught at ${state.width}", onAnchor())
        composeRule.onNodeWithContentDescription(HANDLE).performTouchInput { up() }
    }

    private fun setUp() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                state = rememberExtraPaneWidth()
                // The extra pane's edge in the middle of the window, so the handle sits on screen.
                state.paneEdge = EDGE
                Box(Modifier.fillMaxSize()) { ExtraPaneDragHandle(state, spacerSize = 24.dp) }
            }
        }
        composeRule.framesUntil(limit = 1) { false }
    }

    private fun onAnchor() = ANCHORS.any { abs(it - state.width.value) < TOLERANCE }

    /** Drags the handle by [dx] pixels (beyond the touch slop), lifting the finger after it if [release]. */
    private fun drag(
        dx: Float,
        release: Boolean,
    ) {
        composeRule.onNodeWithContentDescription(HANDLE).performTouchInput {
            down(center)
            repeat(STEPS) { moveBy(Offset(dx / STEPS, 0f)) }
            if (release) up()
        }
    }

    private companion object {
        const val HANDLE = "Resize the side pane"
        const val EDGE = 1000f
        const val DRAG = 70f
        const val SMALL_DRAG = 40f
        const val STEPS = 10
        const val SETTLE_FRAMES = 60
        const val TOLERANCE = 0.5f

        /** The handle's widths on a 1600 dp window: Material's fixed ones and a third of it. */
        val ANCHORS = listOf(360f, 412f, 1600f / 3)
    }
}
