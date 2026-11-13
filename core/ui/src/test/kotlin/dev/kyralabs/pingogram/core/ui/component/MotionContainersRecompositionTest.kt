package dev.kyralabs.pingogram.core.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [ReservedSlot]'s fade and [animateColorProducer]'s color are read in the draw phase (`docs/PERFORMANCE.md` §4): once
 * the change has composed, the frames of the animation recompose nothing.
 */
@RunWith(RobolectricTestRunner::class)
class MotionContainersRecompositionTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val counter = RecompositionCounter()
    private var on by mutableStateOf(false)

    @Test
    fun `a reserved slot's fade recomposes nothing per frame`() {
        setContent { ReservedSlot(on) { Leaf() } }

        assertNothingRecomposesWhileAnimating()
    }

    @Test
    fun `an animated color producer recomposes nothing per frame`() {
        setContent {
            val colors = MaterialTheme.colorScheme
            ColoredText(animateColorProducer(if (on) colors.primary else colors.onSurface))
        }

        assertNothingRecomposesWhileAnimating()
    }

    /** Flips [on], lets the change compose in its first frame, then checks the animation's frames recompose nothing. */
    private fun assertNothingRecomposesWhileAnimating() {
        on = true
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        counter.reset()

        repeat(ANIMATION_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()

        assertEquals(emptyList<String>(), counter.recomposedOwnFunctions)
    }

    private fun setContent(content: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { PingogramTheme(dynamicColor = false) { content() } }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        counter.start()
    }

    private companion object {
        const val ANIMATION_FRAMES = 20
    }
}

@Composable
private fun Leaf() {
    BasicText("Saving…")
}

@Composable
private fun ColoredText(color: androidx.compose.ui.graphics.ColorProducer) {
    BasicText("Pingogram", color = color)
}
