package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [TypingDots] pulse in turn on the motion scheme's spring and draw the pulse without composing again
 * (`docs/PERFORMANCE.md` §4). Compose UI tests cancel infinite animations, so the test runs the pulse itself, frame by
 * frame on the test clock, while [RecompositionCounter] watches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class TypingDotsTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val counter = RecompositionCounter()

    /** False until the counter runs, so it sees the dots' first composition. */
    private var shown by mutableStateOf(false)

    @Test
    fun `the dots brighten in turn without composing anything again`() {
        val state = TypingDotsState()
        lateinit var scope: CoroutineScope
        lateinit var spec: FiniteAnimationSpec<Float>
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                scope = rememberCoroutineScope()
                spec = MaterialTheme.motionScheme.fastEffectsSpec()
                if (shown) {
                    TypingDots(MaterialTheme.colorScheme.primary, MaterialTheme.typography.bodySmall, state = state)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        counter.start()
        shown = true
        repeat(SETTLE_FRAMES) { frame() }
        counter.reset()

        scope.launch { state.pulse(spec) }
        val levels = List(PULSE_FRAMES) { frame().let { List(DOTS) { dot -> state.level(dot) } } }

        val brightest = List(DOTS) { dot -> levels.indexOfFirst { it[dot] > BRIGHT } }
        assertTrue(brightest.toString(), brightest.all { it >= 0 })
        assertEquals(brightest.sorted(), brightest)
        assertEquals(emptyList<String>(), counter.recomposedOwn)
    }

    private fun frame() {
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private companion object {
        const val DOTS = 3
        const val BRIGHT = 0.9f
        const val SETTLE_FRAMES = 10
        const val PULSE_FRAMES = 60
    }
}
