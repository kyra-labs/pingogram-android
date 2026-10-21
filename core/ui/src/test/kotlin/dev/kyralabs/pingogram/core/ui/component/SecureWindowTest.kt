package dev.kyralabs.pingogram.core.ui.component

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** [SecureWindow] asks [SecureWindowRequests] for a secure window exactly while it is composed and secure. */
@RunWith(RobolectricTestRunner::class)
class SecureWindowTest {
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

    private val requests = SecureWindowRequests()

    @Test
    fun `the window is secure while a screen asks, and no longer when it stops`() {
        var secure by mutableStateOf(true)
        composeRule.setContent {
            CompositionLocalProvider(LocalSecureWindow provides requests) { SecureWindow(secure) }
        }
        assertTrue(requests.isRequested)

        secure = false
        composeRule.waitForIdle()

        assertFalse(requests.isRequested)
    }

    @Test
    fun `the request ends when the screen leaves the composition`() {
        var shown by mutableStateOf(true)
        composeRule.setContent {
            CompositionLocalProvider(LocalSecureWindow provides requests) {
                if (shown) SecureWindow(secure = true)
            }
        }
        assertTrue(requests.isRequested)

        shown = false
        composeRule.waitForIdle()

        assertFalse(requests.isRequested)
    }

    @Test
    fun `the window stays secure while any screen still asks, as a secret chat and its media viewer in a transition`() {
        var first by mutableStateOf(true)
        var second by mutableStateOf(true)
        composeRule.setContent {
            CompositionLocalProvider(LocalSecureWindow provides requests) {
                SecureWindow(first)
                SecureWindow(second)
            }
        }

        first = false
        composeRule.waitForIdle()
        assertTrue(requests.isRequested)

        second = false
        composeRule.waitForIdle()
        assertFalse(requests.isRequested)
    }

    @Test
    fun `a screen that does not ask leaves the window alone`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalSecureWindow provides requests) { SecureWindow(false) }
        }

        assertFalse(requests.isRequested)
    }
}
