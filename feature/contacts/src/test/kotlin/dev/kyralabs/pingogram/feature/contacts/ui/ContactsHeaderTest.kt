package dev.kyralabs.pingogram.feature.contacts.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.feature.contacts.R
import dev.kyralabs.pingogram.feature.contacts.ui.preview.ContactsPreviewData
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Contacts tab's large header (`docs/DESIGN_SYSTEM.md` §6 "Contacts list"; owner decision 2026-11-07): its title a
 * heading, shorter once the list scrolls, with goldens of the collapsed header (light, dark).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ContactsHeaderTest {
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
    fun `scrolling the list collapses the header, which keeps naming the tab as a heading`() {
        show(ScreenshotVariant.Light)
        val title = composeRule.onNode(hasText(string(R.string.contacts_title)) and isHeading())
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
    fun collapsedLight() = captureCollapsed(ScreenshotVariant.Light, "collapsedLight")

    @Test
    fun collapsedDark() = captureCollapsed(ScreenshotVariant.Dark, "collapsedDark")

    private fun captureCollapsed(
        variant: ScreenshotVariant,
        name: String,
    ) {
        show(variant)
        collapse()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/${javaClass.name}.$name.png")
    }

    private fun show(variant: ScreenshotVariant) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                ContactsScreen(state = ContactsPreviewData.long, onEvent = {}, onAddContact = {}, onEditContact = {})
            }
        }
        composeRule.waitForIdle()
    }

    private fun collapse() {
        composeRule.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
    }

    private fun string(id: Int) = RuntimeEnvironment.getApplication().getString(id)
}
