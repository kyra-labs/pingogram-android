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

/** [HideNavigationBar] asks [NavigationBarVisibility] to hide the bar exactly while it is composed and hidden. */
@RunWith(RobolectricTestRunner::class)
class NavigationBarVisibilityTest {
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

    private val visibility = NavigationBarVisibility()

    @Test
    fun `the bar hides while a screen asks and returns when it stops asking`() {
        var hidden by mutableStateOf(true)
        composeRule.setContent {
            CompositionLocalProvider(LocalNavigationBarVisibility provides visibility) { HideNavigationBar(hidden) }
        }
        assertTrue(visibility.isHidden)

        hidden = false
        composeRule.waitForIdle()

        assertFalse(visibility.isHidden)
    }

    @Test
    fun `the bar returns when the screen leaves the composition`() {
        var shown by mutableStateOf(true)
        composeRule.setContent {
            CompositionLocalProvider(LocalNavigationBarVisibility provides visibility) {
                if (shown) HideNavigationBar(hidden = true)
            }
        }
        assertTrue(visibility.isHidden)

        shown = false
        composeRule.waitForIdle()

        assertFalse(visibility.isHidden)
    }

    @Test
    fun `the bar stays hidden while any screen still asks`() {
        var first by mutableStateOf(true)
        var second by mutableStateOf(true)
        composeRule.setContent {
            CompositionLocalProvider(LocalNavigationBarVisibility provides visibility) {
                HideNavigationBar(first)
                HideNavigationBar(second)
            }
        }

        first = false
        composeRule.waitForIdle()
        assertTrue(visibility.isHidden)

        second = false
        composeRule.waitForIdle()
        assertFalse(visibility.isHidden)
    }

    @Test
    fun `a screen that does not ask leaves the bar alone`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalNavigationBarVisibility provides visibility) { HideNavigationBar(false) }
        }

        assertFalse(visibility.isHidden)
    }
}
