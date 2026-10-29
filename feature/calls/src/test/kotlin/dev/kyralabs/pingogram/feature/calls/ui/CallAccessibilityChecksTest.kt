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
 * The call screen's states have named controls with 48 dp touch targets ([assertAccessible],
 * `docs/audit/accessibility.md`), on a compact phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CallAccessibilityChecksTest {
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
    fun incoming() = check(CallPreviewStates.incoming)

    @Test
    fun connected() = check(CallPreviewStates.shareOffered)

    @Test
    fun sharingScreen() = check(CallPreviewStates.sharingScreen)

    @Test
    fun video() = check(CallPreviewStates.video)

    @Test
    fun sharingScreenVideo() = check(CallPreviewStates.sharingScreenVideo)

    @Test
    fun remoteScreen() = check(CallPreviewStates.remoteScreen)

    @Test
    fun microphone() = check(CallPreviewStates.microphone)

    private fun check(state: CallUiState) {
        composeRule.setContent { PingogramTheme(dynamicColor = false) { CallScreen(state, {}, {}, {}, {}) } }
        composeRule.assertAccessible()
    }
}
