package dev.kyralabs.pingogram.core.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.component.ComponentActivityRegistration
import dev.kyralabs.pingogram.core.ui.component.TestDurationScale
import dev.kyralabs.pingogram.core.ui.component.framesUntil
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The motion helpers' transitions (`docs/plans/motion-expressive-plan.md` §3.1): each runs to its end over several
 * frames on the theme's springs; with animations off (reduced motion) each ends in its next frame; and the fast exit
 * ends before the default one.
 */
@RunWith(RobolectricTestRunner::class)
class MotionTransitionsTest {
    private val durationScale = TestDurationScale()

    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>(effectContext = durationScale)

    @Test
    fun `each enter and exit runs to its end over several frames`() {
        transitions.forEach { (name, transition) ->
            val (enter, exit) = transition
            val frames = framesToShowAndHide(enter, exit)
            assertTrue("$name in took ${frames.first} frames", frames.first in 2 until FRAME_LIMIT)
            assertTrue("$name out took ${frames.second} frames", frames.second in 2 until FRAME_LIMIT)
        }
    }

    @Test
    fun `with animations off each enter and exit ends at once`() {
        durationScale.scaleFactor = 0f
        transitions.forEach { (name, transition) ->
            val (enter, exit) = transition
            val frames = framesToShowAndHide(enter, exit)
            assertTrue("$name took $frames frames", frames.first <= AT_ONCE && frames.second <= AT_ONCE)
        }
    }

    @Test
    fun `the fast vertical shrink ends before the default one`() {
        val default = framesToShowAndHide({ growInVertically(it) }, { shrinkOutVertically(it) }).second
        val fast = framesToShowAndHide({ growInVertically(it) }, { shrinkOutVertically(it, fast = true) }).second

        assertTrue("fast $fast frames, default $default", fast < default)
    }

    @Test
    fun `a state crossfade runs to its end, and with animations off ends at once`() {
        val frames = framesToSwap()
        assertTrue("$frames frames", frames in AT_ONCE + 1 until FRAME_LIMIT)

        durationScale.scaleFactor = 0f
        val still = framesToSwap()
        assertTrue("$still frames", still <= AT_ONCE)
    }

    /** How many frames [enter] takes to show a box, then [exit] to hide it. */
    private fun framesToShowAndHide(
        enter: (MotionScheme) -> EnterTransition,
        exit: (MotionScheme) -> ExitTransition,
    ): Pair<Int, Int> {
        val state = MutableTransitionState(false)
        setContent {
            val motionScheme = MaterialTheme.motionScheme
            AnimatedVisibility(state, enter = enter(motionScheme), exit = exit(motionScheme)) {
                Box(Modifier.size(BOX))
            }
        }
        state.targetState = true
        val shown = composeRule.framesUntil(FRAME_LIMIT) { state.isIdle }
        state.targetState = false
        val hidden = composeRule.framesUntil(FRAME_LIMIT) { state.isIdle }
        return shown to hidden
    }

    /** How many frames [stateCrossfade] takes to swap a small box for a large one. */
    private fun framesToSwap(): Int {
        val state = MutableTransitionState(false)
        setContent {
            val motionScheme = MaterialTheme.motionScheme
            rememberTransition(state).AnimatedContent(transitionSpec = stateCrossfade(motionScheme)) { large ->
                Box(Modifier.size(if (large) BOX * 2 else BOX))
            }
        }
        state.targetState = true
        return composeRule.framesUntil(FRAME_LIMIT) { state.isIdle }
    }

    /** The content under test, replaced by each case: the rule sets its content once per test. */
    private var shown: (@Composable () -> Unit)? by mutableStateOf(null)
    private var contentSet = false

    private fun setContent(content: @Composable () -> Unit) {
        if (!contentSet) {
            composeRule.mainClock.autoAdvance = false
            composeRule.setContent { PingogramTheme(dynamicColor = false) { shown?.let { key(it) { it() } } } }
            contentSet = true
        }
        shown = content
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private companion object {
        val BOX = 100.dp
        const val FRAME_LIMIT = 120

        /**
         * With animations off a transition ends in the frame that starts it, or the one after, which removes what left.
         */
        const val AT_ONCE = 2

        val transitions: List<Pair<String, Pair<(MotionScheme) -> EnterTransition, (MotionScheme) -> ExitTransition>>> =
            listOf(
                "vertical" to transition({ growInVertically(it) }, { shrinkOutVertically(it) }),
                "horizontal" to transition({ growInHorizontally(it) }, { shrinkOutHorizontally(it) }),
                "pop" to transition({ popIn(it) }, { popOut(it) }),
                "effects" to transition(::effectsEnter, ::effectsExit),
            )
    }
}

private fun transition(
    enter: (MotionScheme) -> EnterTransition,
    exit: (MotionScheme) -> ExitTransition,
): Pair<(MotionScheme) -> EnterTransition, (MotionScheme) -> ExitTransition> = enter to exit
