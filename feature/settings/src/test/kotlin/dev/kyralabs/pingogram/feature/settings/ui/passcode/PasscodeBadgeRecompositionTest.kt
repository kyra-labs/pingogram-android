package dev.kyralabs.pingogram.feature.settings.ui.passcode

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.RecompositionCounter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The passcode badge fades between the lock and the error in the draw phase (`docs/plans/motion-expressive-plan.md`
 * C-6, M-118): a wrong passcode recomposes the badge once for the change, not once per frame of the fade.
 */
@RunWith(RobolectricTestRunner::class)
class PasscodeBadgeRecompositionTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var isError by mutableStateOf(false)

    @Test
    fun `the badge's fade recomposes nothing per frame`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { PingogramTheme(dynamicColor = false) { PasscodeBadge(isError) } }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        val counter = RecompositionCounter()
        counter.start()

        isError = true
        repeat(FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }

        val badge = counter.recomposedOwnFunctions.count { it.endsWith("PasscodeBadge") }
        assertTrue("recomposed: ${counter.recomposedOwnFunctions}", badge <= 1)
    }

    private companion object {
        const val FRAMES = 20
    }
}
