package dev.kyralabs.pingogram.feature.calls.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.domain.model.CallProblem
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * "Rate this call" has named controls with 48 dp touch targets ([assertAccessible], `docs/audit/accessibility.md`),
 * its stars are a radio group, and "Send" sends the stars with the problems only below four stars.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CallRatingAccessibilityChecksTest {
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

    private var sent: Pair<Int, Set<CallProblem>>? = null

    private fun show(isVideo: Boolean = true) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CallRatingContent(isVideo = isVideo, onSend = {
                    stars,
                    problems,
                    ->
                    sent = stars to problems
                }, onNotNow = {})
            }
        }
    }

    @Test
    fun unrated() {
        show()
        composeRule.assertAccessible()
    }

    @Test
    fun `two stars ask what went wrong, and send the picked problems`() {
        show()
        composeRule.onNodeWithContentDescription("2 stars").performClick().assertIsSelected()
        composeRule.onNodeWithText("Echo").performClick()
        composeRule.assertAccessible()

        composeRule.onNodeWithText("Send").performClick()

        assertEquals(2 to setOf(CallProblem.Echo), sent)
    }

    @Test
    fun `four stars send no problems`() {
        show()
        composeRule.onNodeWithContentDescription("2 stars").performClick()
        composeRule.onNodeWithText("Echo").performClick()
        composeRule.onNodeWithContentDescription("4 stars").performClick()

        composeRule.onNodeWithText("Send").performClick()

        assertEquals(4 to emptySet<CallProblem>(), sent)
    }
}
