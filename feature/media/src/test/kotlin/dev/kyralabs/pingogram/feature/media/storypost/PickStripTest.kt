package dev.kyralabs.pingogram.feature.media.storypost

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.media.ui.COMPACT
import dev.kyralabs.pingogram.feature.media.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * New story's strip (board 10a, M-079 of the motion audit): a tile dropped after a drag settles into its place on the
 * default spatial spring, over several frames, instead of jumping there in the frame the finger lifts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PickStripTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a dropped tile settles into its place over several frames`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { PickStrip(state = NewStoryPreviewData.picked, onEvent = {}) }
        }
        val tile = composeRule.onNodeWithContentDescription("Photo 1 of 4")
        val place = tile.getBoundsInRoot().left

        // Long-press the tile and drag it a little toward the next one: less than half a tile, so it keeps its place.
        tile.performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + LONG_PRESS_MARGIN_MILLIS)
            repeat(DRAG_STEPS) { moveBy(Offset(DRAG_STEP_PX, 0f)) }
        }
        composeRule.waitForIdle()
        val dragged = tile.getBoundsInRoot().left
        assertTrue("dragged ${dragged - place} from its place", dragged - place > NEAR)

        composeRule.mainClock.autoAdvance = false
        tile.performTouchInput { up() }
        composeRule.mainClock.advanceTimeByFrame()
        val afterRelease = tile.getBoundsInRoot().left
        assertTrue("jumped back at the release: ${afterRelease - place}", afterRelease - place > NEAR)

        repeat(SETTLE_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        assertEquals(place.value, tile.getBoundsInRoot().left.value, NEAR.value)
    }

    private companion object {
        const val LONG_PRESS_MARGIN_MILLIS = 100L
        const val DRAG_STEPS = 6
        const val DRAG_STEP_PX = 10f

        /** Closer than this to its place counts as at its place. */
        val NEAR: Dp = 1.dp

        /** Enough frames for the default spatial spring to settle. */
        const val SETTLE_FRAMES = 90
    }
}
