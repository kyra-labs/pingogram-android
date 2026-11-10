package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.dates.DateTimeContent
import dev.kyralabs.pingogram.feature.chat.ui.preview.DatePreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The date sheet on screen: "Add to calendar" and "Copy" send their events; the sheet and a conversation with dates
 * have a name and a touch target for every control.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class DateScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()

    @Test
    fun `the sheet's rows send their events and have names and touch targets`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { DateTimeContent(DatePreviewData.sheet, onEvent = { events += it }) }
        }

        composeRule.onNodeWithText(string(R.string.date_time_add_to_calendar)).performClick()
        composeRule.onNodeWithText(string(R.string.date_time_copy)).performClick()
        composeRule.assertAccessible()

        assertEquals(listOf<ChatEvent>(DateTimeEvent.AddToCalendar, DateTimeEvent.Copy), events)
    }

    @Test
    fun `a conversation with dates has names and touch targets`() {
        composeRule.setContent {
            PingogramTheme(
                dynamicColor = false,
            ) { ChatScreen(state = DatePreviewData.messages, onEvent = {}, onBack = {}) }
        }

        composeRule.assertAccessible()
    }

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)
}
