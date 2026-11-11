package dev.kyralabs.pingogram.feature.media.storypost

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.media.ui.COMPACT
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
 * A story's "Selected people" has named controls with 48 dp touch targets ([assertAccessible],
 * `docs/audit/accessibility.md`), its contacts are checkboxes, and "Done" waits for a pick.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StoryPeopleAccessibilityChecksTest {
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

    private val toggled = mutableListOf<Long>()
    private var done = 0

    private fun show(state: StoryPeopleUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                StoryPeopleContent(state, "") { event ->
                    when (event) {
                        is StoryPeopleEvent.Toggle -> toggled += event.userId
                        StoryPeopleEvent.Done -> done++
                        else -> Unit
                    }
                }
            }
        }
    }

    @Test
    fun contacts() {
        show(StoryPeoplePreviewData.contacts)

        composeRule.assertAccessible()
        composeRule.onNodeWithText("Grace Hopper").assertIsOn()
        composeRule.onNodeWithText("Alan Turing").performClick()
        composeRule.onNodeWithText("Done (2)").performClick()

        assertEquals(listOf(8L), toggled)
        assertEquals(1, done)
    }

    @Test
    fun noContacts() {
        show(StoryPeoplePreviewData.noContacts)

        composeRule.assertAccessible()
        composeRule.onNodeWithText("Done").assertIsNotEnabled()
    }
}
