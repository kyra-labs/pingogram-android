package dev.kyralabs.pingogram.core.ui.component

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * [SecureWindowRequests.isRequested]: what reads it composes again only when the window must turn secure or may stop
 * being so, not when a second screen asks too (the media viewer over a secret chat), which set the window's flag
 * again, a relayout (`docs/audit/performance-review-2026-11.md`, M3).
 */
@RunWith(RobolectricTestRunner::class)
class SecureWindowRequestsTest {
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
    fun `a second request recomposes nothing that reads it`() {
        val requests = SecureWindowRequests()
        val seen = mutableListOf<Boolean>()
        composeRule.setContent {
            val requested = requests.isRequested
            SideEffect { seen += requested }
        }

        for (change in listOf(requests::request, requests::request, requests::release, requests::release)) {
            composeRule.runOnIdle { change() }
            composeRule.waitForIdle()
        }

        assertEquals(listOf(false, true, false), seen)
    }
}
