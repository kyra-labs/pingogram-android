package dev.kyralabs.pingogram.core.ui.theme

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalRippleThemeConfiguration
import androidx.compose.material3.RippleDefaults
import androidx.compose.material3.RippleThemeConfiguration
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** In [PingogramTheme], keyboard focus shows Material 3 Expressive's inset focus rings (`DESIGN_SYSTEM.md` §2.3). */
@RunWith(RobolectricTestRunner::class)
class FocusRingTest {
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

    @Test
    fun `the theme provides the inset focus ring configuration`() {
        var configuration: RippleThemeConfiguration? = null
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { configuration = LocalRippleThemeConfiguration.current }
        }
        composeRule.waitForIdle()

        assertSame(RippleDefaults.InsetFocusRingThemeConfiguration, configuration)
    }
}
