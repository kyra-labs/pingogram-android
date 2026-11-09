package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Settings home's large header, the tabs' one (`docs/DESIGN_SYSTEM.md` §6 "Settings rows"; owner decision
 * 2026-11-07): its title a heading, shorter once the page scrolls, with goldens of the collapsed header (light, dark).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SettingsHeaderTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `scrolling the page collapses the header, which keeps naming the tab as a heading`() {
        show(ThemeMode.Light)
        val title = composeRule.onNode(hasText(string(R.string.settings_title)) and isHeading())
        val expanded = title.fetchSemanticsNode().boundsInRoot.height

        collapse()

        val collapsed =
            title
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot.height
        assertTrue("$collapsed < $expanded", collapsed < expanded)
    }

    @Test
    fun collapsedLight() = captureCollapsed(ThemeMode.Light, "collapsedLight")

    @Test
    fun collapsedDark() = captureCollapsed(ThemeMode.Dark, "collapsedDark")

    private fun captureCollapsed(
        themeMode: ThemeMode,
        name: String,
    ) {
        show(themeMode)
        collapse()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/${javaClass.name}.$name.png")
    }

    private fun show(themeMode: ThemeMode) {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                SettingsScreen(state = SettingsPreviewData.content, onEvent = {}, onOpenPage = {})
            }
        }
        composeRule.waitForIdle()
    }

    private fun collapse() {
        composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
    }

    private fun string(id: Int) = RuntimeEnvironment.getApplication().getString(id)
}
