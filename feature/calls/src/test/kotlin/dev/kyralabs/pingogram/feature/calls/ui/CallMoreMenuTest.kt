package dev.kyralabs.pingogram.feature.calls.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.calls.R
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
 * The call panel's More (board 27, owner decision 2026-11-15: Hold moves into More): it holds Hold (or Resume) and
 * "Add people", which left the top bar; and its golden with the menu open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CallMoreMenuTest {
    @get:Rule(order = 0)
    val activityRegistration =
        object : ExternalResource() {
            override fun before() {
                val application = RuntimeEnvironment.getApplication()
                val packageManager = shadowOf(application.packageManager)
                val activity =
                    packageManager.addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
                // No action bar: the golden shows only the screen, as the app's edge-to-edge activity does.
                activity.theme = android.R.style.Theme_Material_Light_NoActionBar
                packageManager.addOrUpdateActivity(activity)
            }
        }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<CallEvent>()
    private var state by mutableStateOf<CallUiState>(CallPreviewStates.shareOffered)

    private fun setScreen(initial: CallUiState) {
        state = initial
        composeRule.setContent {
            PingogramTheme(
                dynamicColor = false,
            ) { CallScreen(state, { events += it }, {}, {}, {}) }
        }
        composeRule.waitForIdle()
    }

    private fun string(id: Int) = composeRule.activity.getString(id)

    @Test
    fun `More holds Hold and Add people, which the top bar no longer has`() {
        setScreen(CallPreviewStates.shareOffered)
        composeRule.onNode(hasText(string(R.string.call_hold))).assertDoesNotExist()
        composeRule.onNode(hasContentDescription(string(R.string.group_call_add_people))).assertDoesNotExist()

        composeRule.onNode(hasContentDescription(string(R.string.call_more))).performClick()
        composeRule.onNode(hasText(string(R.string.group_call_add_people))).assertIsDisplayed()
        composeRule.onNode(hasText(string(R.string.call_hold))).performClick()

        assertEquals(CallEvent.ToggleHold, events.last())
    }

    @Test
    fun `a held call offers Resume, and Add people only where it can`() {
        setScreen(CallPreviewStates.connected.copy(isOnHold = true))

        composeRule.onNode(hasContentDescription(string(R.string.call_more))).performClick()

        composeRule.onNode(hasText(string(R.string.call_resume))).assertIsDisplayed()
        composeRule.onNode(hasText(string(R.string.group_call_add_people))).assertDoesNotExist()
    }

    @Test
    fun `before the call connects Hold waits`() {
        setScreen(CallPreviewStates.ringing)

        composeRule.onNode(hasContentDescription(string(R.string.call_more))).performClick()

        composeRule.onNode(hasText(string(R.string.call_hold))).assertIsNotEnabled()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun moreMenu() {
        setScreen(CallPreviewStates.shareOffered)
        composeRule.onNode(hasContentDescription(string(R.string.call_more))).performClick()
        composeRule.waitForIdle()

        captureScreenRoboImage("src/test/snapshots/${javaClass.name}.moreMenu.png")
    }
}
