package dev.kyralabs.pingogram.feature.contacts.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.contacts.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The Contacts tab until the contacts list lands: a titled bar, and New chat as the next step. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ContactsScreenTest {
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

    private var newChats = 0

    @Test
    fun `the top bar names the tab as a heading`() {
        setScreen()

        composeRule.onNode(hasText(string(R.string.contacts_title)) and isHeading()).assertIsDisplayed()
    }

    @Test
    fun `New chat is offered while contacts are not there yet`() {
        setScreen()

        composeRule.onNode(hasText(string(R.string.contacts_new_chat)) and hasClickAction()).performClick()

        assertEquals(1, newChats)
    }

    private fun setScreen() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { ContactsScreen(onOpenNewChat = { newChats++ }) }
        }
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)
}
