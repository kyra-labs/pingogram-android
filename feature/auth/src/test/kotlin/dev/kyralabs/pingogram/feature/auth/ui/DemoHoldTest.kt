package dev.kyralabs.pingogram.feature.auth.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The hidden way into the demo (`docs/adr/0034-demo-mode-for-store-review.md`): holding Welcome's logo for a second
 * asks to open the demo; a tap, a shorter press or a drag across the logo does not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class DemoHoldTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<AuthEvent>()

    private fun showWelcome() {
        composeRule.setContent {
            ScreenshotTheme(ScreenshotVariant.Light) {
                AuthScreen(state = AuthPreviewData.all.getValue("welcome"), onEvent = { events += it })
            }
        }
    }

    private fun logo() = composeRule.onNodeWithTag(WELCOME_LOGO_TAG, useUnmergedTree = true)

    private fun demoAsks() = events.count { it == AuthEvent.ShowDemoDialog }

    @Test
    fun `holding the logo for a second asks to open the demo`() {
        showWelcome()

        logo().performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(HELD_MILLIS)
        logo().performTouchInput { up() }
        composeRule.waitForIdle()

        assertEquals(1, demoAsks())
    }

    @Test
    fun `a short press does not`() {
        showWelcome()

        logo().performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(SHORT_MILLIS)
        logo().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(HELD_MILLIS)
        composeRule.waitForIdle()

        assertEquals(0, demoAsks())
    }

    @Test
    fun `a drag across the logo does not`() {
        showWelcome()

        logo().performTouchInput {
            down(center)
            moveBy(Offset(0f, height * 2f))
        }
        composeRule.mainClock.advanceTimeBy(HELD_MILLIS)
        logo().performTouchInput { up() }
        composeRule.waitForIdle()

        assertEquals(0, demoAsks())
    }

    private companion object {
        const val HELD_MILLIS = 1_200L
        const val SHORT_MILLIS = 300L
    }
}
