package dev.kyralabs.pingogram.feature.calls.ui

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
 * Every state of the voice chat's screen has named controls with 48 dp touch targets ([assertAccessible],
 * `docs/audit/accessibility.md`), on a compact phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class GroupCallAccessibilityChecksTest {
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
    fun connected() = check(GroupCallPreviewStates.connected)

    @Test
    fun camera() = check(GroupCallPreviewStates.camera)

    @Test
    fun videos() = check(GroupCallPreviewStates.videos)

    @Test
    fun sharingScreen() = check(GroupCallPreviewStates.sharingScreen)

    @Test
    fun screens() = check(GroupCallPreviewStates.screens)

    @Test
    fun raiseHand() = check(GroupCallPreviewStates.raiseHand)

    @Test
    fun stream() = check(GroupCallPreviewStates.stream)

    @Test
    fun microphone() = check(GroupCallPreviewStates.microphone)

    @Test
    fun unavailable() = check(GroupCallPreviewStates.unavailable)

    @Test
    fun confirmConference() = check(GroupCallPreviewStates.confirmConference)

    @Test
    fun conference() = check(GroupCallPreviewStates.conference)

    private fun check(state: GroupCallUiState) {
        composeRule.setContent { PingogramTheme(dynamicColor = false) { GroupCallScreen(state, {}, {}, {}, {}) } }
        composeRule.assertAccessible()
    }
}
