package dev.kyralabs.pingogram.feature.feedback

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * "Send feedback" has named controls with 48 dp touch targets ([assertAccessible], `docs/audit/accessibility.md`),
 * with the report hidden and shown, and while it loads, on a compact phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class FeedbackAccessibilityChecksTest {
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

    @Test
    fun content() = check(FeedbackSamples.content(description = "It froze"))

    @Test
    fun reportShown() = check(FeedbackSamples.content(showReport = true))

    @Test
    fun loading() = check(FeedbackUiState.Loading)

    private fun check(state: FeedbackUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { FeedbackScreen(state = state, onEvent = {}, onBack = {}) }
        }
        composeRule.assertAccessible()
    }
}
