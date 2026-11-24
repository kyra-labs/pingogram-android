package dev.kyralabs.pingogram.feature.contacts.ui.birthdays

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.contacts.ui.COMPACT
import dev.kyralabs.pingogram.feature.contacts.ui.preview.BirthdaysPreviewStates
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
 * The Birthdays page (`docs/DESIGN_SYSTEM.md` §6 "Birthdays"): its groups, a row opening the chat, "Remind me" as a
 * toggle with its state, "Add to calendar", the empty state, and every control named with a 48 dp target.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BirthdaysScreenTest {
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

    private val events = mutableListOf<BirthdaysEvent>()

    private fun setPage(state: BirthdaysUiState = BirthdaysPreviewStates().values.first()) =
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                BirthdaysScreen(state = state, onEvent = { events += it }, onBack = {})
            }
        }

    @Test
    fun `the groups show under their headers, and a row opens the chat`() {
        setPage()

        listOf("Today", "Coming up", "Recently").forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        composeRule.onNodeWithText("November 24 · turns 36").assertIsDisplayed()
        composeRule.onNodeWithText("Ada Lovelace").performClick()

        assertEquals(listOf<BirthdaysEvent>(BirthdaysEvent.Open(2)), events)
    }

    @Test
    fun `remind me is a toggle with its state, and add to calendar asks for the event`() {
        setPage()

        composeRule.onNodeWithContentDescription("Remind me of Ada Lovelace's birthday").assertIsOn().performClick()
        composeRule.onNodeWithContentDescription("Remind me of Grace Hopper's birthday").assertIsOff().performClick()
        composeRule.onNodeWithContentDescription("Add Alan Turing's birthday to calendar").performClick()

        assertEquals(
            listOf(
                BirthdaysEvent.SetReminder(2, on = false),
                BirthdaysEvent.SetReminder(4, on = true),
                BirthdaysEvent.AddToCalendar(11),
            ),
            events,
        )
    }

    @Test
    fun `without birthdays the page says so`() {
        setPage(BirthdaysUiState.Empty)

        composeRule.onNodeWithText("No birthdays soon").assertIsDisplayed()
    }

    @Test
    fun `every control is named with a 48 dp target`() {
        setPage()

        composeRule.assertAccessible()
    }
}
