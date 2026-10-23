package dev.kyralabs.pingogram.feature.calls.history

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.calls.ui.COMPACT
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Every state of Recent calls has named controls with 48 dp touch targets ([assertAccessible],
 * `docs/audit/accessibility.md`), on a compact phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class RecentCallsAccessibilityChecksTest {
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
    fun calls() = check(RecentCallsPreviewStates.calls)

    @Test
    fun pageError() = check(RecentCallsPreviewStates.pageError)

    @Test
    fun empty() = check(RecentCallsPreviewStates.empty)

    @Test
    fun error() = check(RecentCallsPreviewStates.error)

    private fun check(state: RecentCallsUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { RecentCallsScreen(state, onEvent = {}, onBack = {}) }
        }
        composeRule.assertAccessible()
    }
}
