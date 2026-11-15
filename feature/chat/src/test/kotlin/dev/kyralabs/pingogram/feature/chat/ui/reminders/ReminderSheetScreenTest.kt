package dev.kyralabs.pingogram.feature.chat.ui.reminders

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.ReminderEvent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The reminder sheet's controls (board 29b): the choices as a radio group, setting and removing. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ReminderSheetScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ReminderEvent>()

    private fun show(state: ReminderSheetUiState = ReminderSheetPreviewData.new) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { Surface { ReminderSheetContent(state, onEvent = { events += it }) } }
        }
    }

    @Test
    fun `the chosen time is selected and another is chosen`() {
        show()

        composeRule.onNodeWithText("Tomorrow morning", useUnmergedTree = false).assertIsSelected()
        composeRule.onNodeWithText("In 1 hour").performClick()

        assertEquals(listOf<ReminderEvent>(ReminderEvent.Choose(ReminderOption.InOneHour)), events)
    }

    @Test
    fun `setting sends the note and what the message says`() {
        show()

        composeRule.onNodeWithText("Set reminder").performScrollTo().performClick()

        assertEquals(
            listOf<ReminderEvent>(
                ReminderEvent.Save(
                    note = "",
                    sender = "Meera Iyer",
                    text = "Can you check the price for 4 people? The good homestay fills up fast",
                ),
            ),
            events,
        )
    }

    @Test
    fun `a picked date and time without a time cannot be set`() {
        show(ReminderSheetPreviewData.new.copy(selected = ReminderOption.Custom))

        composeRule.onNodeWithText("Set reminder").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `a reminder set already offers its removal`() {
        show(ReminderSheetPreviewData.set)

        composeRule.onNodeWithText("Remove reminder").performScrollTo().performClick()

        assertEquals(listOf<ReminderEvent>(ReminderEvent.Remove), events)
    }

    @Test
    fun `the sheet is accessible`() {
        show()
        composeRule.assertAccessible()
    }
}
