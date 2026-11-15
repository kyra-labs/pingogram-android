package dev.kyralabs.pingogram.feature.chat.ui.reminders

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.ReminderEvent
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The reminder chip (the owner's board 29c): a tap opens the reminder, and screen readers hear it and open it too. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ReminderChipTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a tap on the chip opens its reminder`() {
        var opened = 0
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ReminderChip(UiText.Plain("Sun, 9:00 AM"), ChipColors.onBackground(), onClick = { opened++ })
            }
        }

        composeRule.onNodeWithText("Sun, 9:00 AM").performClick()

        assertEquals(1, opened)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `screen readers hear the reminder in the bubble and change it through its action`() {
        val events = mutableListOf<ChatEvent>()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                WithReminderChips {
                    ChatScreen(state = ChatPreviewData.privateChat, onEvent = { events += it }, onBack = {})
                }
            }
        }

        composeRule
            .onNode(hasContentDescription("Reminder Sun, 9:00 AM", substring = true))
            .performCustomAccessibilityActionWithLabel("Change reminder")

        assertEquals(listOf<ChatEvent>(ReminderEvent.Open(11)), events.filterIsInstance<ReminderEvent>())
    }
}
