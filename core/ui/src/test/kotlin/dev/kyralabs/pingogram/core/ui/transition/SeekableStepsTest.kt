package dev.kyralabs.pingogram.core.ui.transition

import android.content.ComponentName
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * [rememberSeekableSteps] (`docs/DESIGN_SYSTEM.md` §5.1, Q-08 and Q-19): a back gesture brings the step back leads to
 * in with its progress and goes back once it completes; a cancelled one goes back to nothing and the step it previewed
 * leaves; with animations off the gesture previews nothing and the step changes only when it completes.
 */
@RunWith(RobolectricTestRunner::class)
class SeekableStepsTest {
    // Declares the activity to Robolectric, as ui-test-manifest would.
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

    private var step by mutableStateOf(SECOND)
    private var backs = 0

    @After
    fun animationsOn() = setAnimatorDurationScale(1f)

    @Test
    fun `a completed gesture previews the step before and goes back`() {
        show()

        gesture { dispatcher -> dispatcher.dispatchOnBackProgressed(event(HALF)) }
        val during = shown(FIRST)
        gesture(start = false) { dispatcher -> dispatcher.onBackPressed() }

        assertEquals(1, during)
        assertEquals(1, backs)
    }

    @Test
    fun `a cancelled gesture stays on the step and the preview leaves`() {
        show()

        gesture { dispatcher -> dispatcher.dispatchOnBackProgressed(event(HALF)) }
        gesture(start = false) { dispatcher -> dispatcher.dispatchOnBackCancelled() }

        assertEquals(0, backs)
        assertEquals(0, shown(FIRST))
        assertEquals(1, shown(SECOND))
    }

    @Test
    fun `with animations off the gesture previews nothing`() {
        setAnimatorDurationScale(0f)
        show()

        gesture { dispatcher -> dispatcher.dispatchOnBackProgressed(event(HALF)) }
        val during = shown(FIRST)
        gesture(start = false) { dispatcher -> dispatcher.onBackPressed() }

        assertEquals(0, during)
        assertEquals(1, backs)
    }

    @Test
    fun `without a step to go back to, back is left to the screen below`() {
        step = FIRST
        show()

        val handled = composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks() }

        assertEquals(false, handled)
    }

    private fun show() {
        composeRule.setContent {
            MaterialTheme {
                val steps =
                    rememberSeekableSteps(
                        current = step,
                        backTo = FIRST.takeIf { step == SECOND },
                        key = { it },
                        onBack = {
                            backs++
                            step = FIRST
                        },
                    )
                steps.transition.AnimatedContent(transitionSpec = { fadeIn() togetherWith fadeOut() }) { shown ->
                    Text(shown)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun shown(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun gesture(
        start: Boolean = true,
        gesture: (OnBackPressedDispatcher) -> Unit,
    ) {
        composeRule.runOnUiThread {
            val dispatcher = composeRule.activity.onBackPressedDispatcher
            if (start) dispatcher.dispatchOnBackStarted(event(0f))
            gesture(dispatcher)
        }
        composeRule.waitForIdle()
    }

    private fun event(progress: Float) =
        BackEventCompat(touchX = 0f, touchY = 0f, progress = progress, swipeEdge = BackEventCompat.EDGE_LEFT)

    private companion object {
        const val FIRST = "First step"
        const val SECOND = "Second step"
        const val HALF = 0.5f
    }
}
