package dev.kyralabs.pingogram.feature.calls.history

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.calls.ui.COMPACT
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
 * "Start a call" has named controls with 48 dp touch targets ([assertAccessible], `docs/audit/accessibility.md`), each
 * call button names its person, and a device without a camera offers no video call.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StartCallAccessibilityChecksTest {
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

    private val chosen = mutableListOf<StartCallChoice>()

    private fun show(state: StartCallUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                StartCallContent(state, "", onQuery = {}, onChoose = { chosen += it })
            }
        }
    }

    @Test
    fun contacts() {
        show(StartCallPreviewStates.contacts)

        composeRule.assertAccessible()
        composeRule.onNodeWithContentDescription("Voice call ⁨Grace Hopper⁩").performClick()
        composeRule.onNodeWithContentDescription("Video call ⁨Alan Turing⁩").performClick()
        composeRule.onNodeWithText("New group call").performClick()

        assertEquals(
            listOf(
                StartCallChoice.Person(7, isVideo = false),
                StartCallChoice.Person(8, isVideo = true),
                StartCallChoice.GroupCall,
            ),
            chosen,
        )
    }

    @Test
    fun noCamera() {
        show(StartCallPreviewStates.noCamera)

        composeRule.assertAccessible()
        composeRule.onNodeWithContentDescription("Video call ⁨Grace Hopper⁩").assertDoesNotExist()
    }
}
