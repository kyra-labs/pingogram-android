package dev.kyralabs.pingogram.core.ui.component

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.isAmoledBlack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * [CheckSwitch] shows a check in its thumb exactly while on, [performToggle] buzzes the toggle's haptic, and
 * [isAmoledBlack] says whether the theme's surfaces are true black.
 */
@RunWith(RobolectricTestRunner::class)
class CheckSwitchTest {
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
    fun `the thumb shows a check while on and none while off`() {
        var checked by mutableStateOf(true)
        composeRule.setContent { PingogramTheme(dynamicColor = false) { CheckSwitch(checked = checked) } }
        composeRule.onNodeWithTag(CHECK_SWITCH_CHECK_TAG, useUnmergedTree = true).assertExists()

        checked = false
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CHECK_SWITCH_CHECK_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `turning on buzzes ToggleOn and turning off ToggleOff`() {
        val performed = mutableListOf<HapticFeedbackType>()
        val haptics =
            object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    performed += hapticFeedbackType
                }
            }

        haptics.performToggle(checked = true)
        haptics.performToggle(checked = false)

        assertEquals(listOf(HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOff), performed)
    }

    @Test
    fun `surfaces are true black only in dark theme with AMOLED black on`() {
        val results = mutableMapOf<String, Boolean>()
        composeRule.setContent {
            PingogramTheme(ThemeMode.Dark, dynamicColor = false, amoled = true) {
                results["dark amoled"] =
                    isAmoledBlack()
            }
            PingogramTheme(ThemeMode.Light, dynamicColor = false, amoled = true) {
                results["light amoled"] = isAmoledBlack()
            }
            PingogramTheme(ThemeMode.Dark, dynamicColor = false) { results["dark"] = isAmoledBlack() }
        }
        composeRule.waitForIdle()

        assertEquals(mapOf("dark amoled" to true, "light amoled" to false, "dark" to false), results)
    }
}
