package dev.kyralabs.pingogram.core.ui.component

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/** [windowMargin]: 16 dp in compact windows, 24 dp from medium widths on (`docs/DESIGN_SYSTEM.md` §7). */
@RunWith(RobolectricTestRunner::class)
class WindowMarginTest {
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
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `a phone in portrait keeps the compact margin`() {
        assertEquals(16.dp, margin())
    }

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun `a medium window keeps the medium margin`() {
        assertEquals(24.dp, margin())
    }

    private fun margin(): Dp {
        var margin = Dp.Unspecified
        composeRule.setContent { PingogramTheme(dynamicColor = false) { margin = windowMargin() } }
        composeRule.waitForIdle()
        return margin
    }
}
