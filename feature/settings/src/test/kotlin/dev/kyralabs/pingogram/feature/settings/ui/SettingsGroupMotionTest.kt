package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A [SettingsGroup]'s keyed rows grow in and shrink away as they come and go (`docs/DESIGN_SYSTEM.md` §5.1, Q-21), so
 * what is below them moves over several frames rather than jumping; rows without a key come and go at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SettingsGroupMotionTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var shown by mutableStateOf(false)

    @Test
    fun `a keyed row grows in and shrinks away, moving what is below over several frames`() {
        show(keyed = true)
        val resting = belowTop()

        composeRule.mainClock.autoAdvance = false
        shown = true
        val growing = frames()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        val grown = belowTop()

        composeRule.mainClock.autoAdvance = false
        shown = false
        val shrinking = frames()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertTrue("grew over $growing", growing.count { it > resting && it < grown } > 2)
        assertTrue("shrank over $shrinking", shrinking.count { it > resting && it < grown } > 2)
        assertEquals(resting, belowTop())
    }

    @Test
    fun `a row without a key comes and goes at once`() {
        show(keyed = false)
        val resting = belowTop()

        composeRule.mainClock.autoAdvance = false
        shown = true
        val growing = frames()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        // At its place from the first frame that composes it.
        assertTrue("moved over $growing", growing.drop(1).all { it == belowTop() && it > resting })
    }

    @Test
    fun `rows shown when the group first appears do not grow in`() {
        shown = true
        composeRule.mainClock.autoAdvance = false
        show(keyed = true)
        composeRule.mainClock.advanceTimeByFrame()
        val first = belowTop()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals(belowTop(), first)
    }

    private fun show(keyed: Boolean) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                Column {
                    SettingsGroup {
                        item(key = "first") { shapes -> SettingsRow(FIRST, shapes) }
                        if (shown) {
                            if (keyed) {
                                item(key = "second") { shapes -> SettingsRow(SECOND, shapes) }
                            } else {
                                item { shapes -> SettingsRow(SECOND, shapes) }
                            }
                        }
                    }
                    Text(BELOW)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun frames(): List<Dp> =
        List(FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            belowTop()
        }

    private fun belowTop(): Dp = composeRule.onNodeWithText(BELOW).getUnclippedBoundsInRoot().top

    private companion object {
        const val FIRST = "First"
        const val SECOND = "Second"
        const val BELOW = "Below"
        const val FRAMES = 30
    }
}
