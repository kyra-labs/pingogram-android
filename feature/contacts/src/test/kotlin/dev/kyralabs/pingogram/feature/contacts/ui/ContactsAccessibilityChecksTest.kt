package dev.kyralabs.pingogram.feature.contacts.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.contacts.ui.preview.ContactsPreviewData
import dev.kyralabs.pingogram.feature.contacts.ui.preview.EditContactPreviewData
import dev.kyralabs.pingogram.feature.contacts.ui.preview.NewContactPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Contacts tab's and the contact forms' controls have a name and a 48 dp touch target ([assertAccessible],
 * `docs/audit/accessibility.md`), on a compact phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ContactsAccessibilityChecksTest {
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
    fun contacts() =
        check {
            ContactsScreen(state = ContactsPreviewData.content, onEvent = {}, onAddContact = {}, onEditContact = {})
        }

    @Test
    fun newContact() = check { NewContactScreen(state = NewContactPreviewData.phoneNumber, onEvent = {}, onBack = {}) }

    @Test
    fun notOnTelegram() =
        check { NewContactScreen(state = NewContactPreviewData.notOnTelegram, onEvent = {}, onBack = {}) }

    @Test
    fun editContact() = check { EditContactScreen(state = EditContactPreviewData.contact, onEvent = {}, onBack = {}) }

    private fun check(content: @Composable () -> Unit) {
        composeRule.setContent { PingogramTheme(dynamicColor = false) { content() } }
        composeRule.assertAccessible()
    }
}
