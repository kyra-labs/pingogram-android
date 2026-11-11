package dev.kyralabs.pingogram.core.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The motion helpers' containers (`docs/plans/motion-expressive-plan.md` §3.2): a [ReservedSlot] keeps its place as
 * its content comes and goes, a [GrowingVisibility] moves what follows it over several frames, and a [StateCrossfade]
 * always shows one of its states.
 */
@RunWith(RobolectricTestRunner::class)
class MotionContainersTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var visible by mutableStateOf(false)
    private var large by mutableStateOf(false)

    @Test
    fun `a reserved slot keeps its height as its content comes and goes`() {
        setContent {
            Column {
                ReservedSlot(visible, Modifier.testTag(SLOT)) { Text(PROGRESS) }
                Box(Modifier.testTag(BELOW).size(SIZE))
            }
        }
        val top = belowTop()

        visible = true
        composeRule.framesUntil(limit = SLOT_FRAMES) { false.also { assertEquals(top, belowTop()) } }
        visible = false
        composeRule.framesUntil(limit = SLOT_FRAMES) { false.also { assertEquals(top, belowTop()) } }
    }

    @Test
    fun `a reserved slot's content is out of the semantics tree while hidden`() {
        setContent { ReservedSlot(visible) { Text(PROGRESS) } }

        composeRule.onNodeWithText(PROGRESS).assertDoesNotExist()
        visible = true
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText(PROGRESS).assertExists()
    }

    @Test
    fun `growing visibility moves what follows it over several frames`() {
        setContent {
            Column {
                GrowingVisibility(visible) { Box(Modifier.size(SIZE)) }
                Box(Modifier.testTag(BELOW).size(SIZE))
            }
        }
        val top = belowTop()

        visible = true
        val tops = mutableListOf<Dp>()
        val frames = composeRule.framesUntil { belowTop().also(tops::add) == top + SIZE }

        assertTrue("it took $frames frames: $tops", frames > 2)
        assertTrue("it moved in steps: $tops", tops.any { it > top && it < top + SIZE })
    }

    @Test
    fun `a state crossfade always shows one of its states`() {
        setContent {
            StateCrossfade(large) { isLarge -> Box(Modifier.testTag(if (isLarge) LARGE else SMALL).size(SIZE)) }
        }

        large = true
        val frames =
            composeRule.framesUntil {
                val small = composeRule.onAllNodesWithTag(SMALL).fetchSemanticsNodes().size
                val shownLarge = composeRule.onAllNodesWithTag(LARGE).fetchSemanticsNodes().size
                assertTrue("neither state shows", small + shownLarge > 0)
                small == 0
            }

        assertTrue("the swap took $frames frames", frames > 1)
        composeRule.onNodeWithTag(LARGE).assertExists()
    }

    private fun belowTop(): Dp = composeRule.onNodeWithTag(BELOW).getBoundsInRoot().top

    private fun setContent(content: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { PingogramTheme(dynamicColor = false) { content() } }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private companion object {
        const val SLOT = "slot"
        const val BELOW = "below"
        const val SMALL = "small"
        const val LARGE = "large"
        const val PROGRESS = "Saving…"
        val SIZE = 48.dp
        const val SLOT_FRAMES = 30
    }
}
