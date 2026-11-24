package dev.kyralabs.pingogram.feature.chat.ui.bot

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.BotEvent
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The question before a bot command goes (`REQUIREMENTS.md` FR-X12): its command, "Send" and "Cancel", accessible, and
 * its goldens light and dark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BotCommandConfirmDialogTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()

    private fun show(themeMode: ThemeMode = ThemeMode.Light) =
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                BotCommandConfirmDialog("/help", onEvent = events::add)
            }
        }

    @Test
    fun `send sends the command, cancel does not`() {
        show()
        composeRule.onNodeWithText("/help", substring = true).assertExists()
        composeRule.assertAccessible()

        composeRule.onNodeWithText("Send").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(listOf(BotEvent.ConfirmCommand, BotEvent.CancelCommand), events)
    }

    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        themeMode: ThemeMode,
    ) {
        show(themeMode)
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }
}
