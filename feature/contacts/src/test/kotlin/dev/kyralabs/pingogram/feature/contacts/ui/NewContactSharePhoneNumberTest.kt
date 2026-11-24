package dev.kyralabs.pingogram.feature.contacts.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.contacts.ui.preview.NewContactPreviewData
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
 * Add contact's "Share my phone number" (`REQUIREMENTS.md` FR-X9): a switch for screen readers that sends the
 * choice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class NewContactSharePhoneNumberTest {
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

    private val events = mutableListOf<NewContactEvent>()

    private fun show(state: NewContactUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { NewContactScreen(state = state, onEvent = events::add, onBack = {}) }
        }
    }

    @Test
    fun `off, tapping the row asks to share the number`() {
        show(NewContactPreviewData.phoneNumber)

        composeRule
            .onNodeWithText("Share my phone number")
            .performScrollTo()
            .assertIsOff()
            .performClick()

        assertEquals(listOf<NewContactEvent>(NewContactEvent.SharePhoneNumberChanged(true)), events)
    }

    @Test
    fun `on, it names the contact, and tapping asks to stop sharing`() {
        show(NewContactPreviewData.sharingPhoneNumber)

        // The name is isolated from the text around it (bidi), so it is matched on its own.
        composeRule
            .onNode(hasText("can see your number", substring = true) and hasText("Grace", substring = true))
            .assertExists()
        composeRule.onNodeWithText("They can see your number").assertDoesNotExist()
        composeRule
            .onNodeWithText("Share my phone number")
            .performScrollTo()
            .assertIsOn()
            .performClick()

        assertEquals(listOf<NewContactEvent>(NewContactEvent.SharePhoneNumberChanged(false)), events)
    }

    @Test
    fun `without a name, the summary says they`() {
        show(NewContactPreviewData.phoneNumber.copy(firstName = ""))

        composeRule.onNodeWithText("They can see your number").assertExists()
    }
}
