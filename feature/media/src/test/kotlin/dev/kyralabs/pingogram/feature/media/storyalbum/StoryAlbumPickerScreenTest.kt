package dev.kyralabs.pingogram.feature.media.storyalbum

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.media.ui.COMPACT
import kotlinx.collections.immutable.persistentSetOf
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
 * Picking stories for an album (owner decision 2026-11-20): each story a checkbox, how many are picked, and the
 * change's button, enabled once one is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StoryAlbumPickerScreenTest {
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

    private val events = mutableListOf<StoryAlbumPickerEvent>()

    private fun setPicker(state: StoryAlbumPickerUiState) =
        composeRule.setContent {
            PingogramTheme(
                dynamicColor = false,
            ) { StoryAlbumPickerScreen(state, onEvent = { events += it }, onClose = {}) }
        }

    @Test
    fun `stories are checkboxes, the count says how many are picked, and the button creates the album`() {
        setPicker(StoryAlbumPreviewData.picking)

        composeRule.onNodeWithText("2 selected").assertExists()
        composeRule.onNodeWithContentDescription("Story from Oct 3").assertIsOn()
        composeRule.onNodeWithContentDescription("Video story from Oct 5").assertIsOff().performClick()
        composeRule.onNodeWithText("Create album").assertIsEnabled().performClick()

        assertEquals(listOf(StoryAlbumPickerEvent.Toggle(6), StoryAlbumPickerEvent.Confirm), events)
        composeRule.assertAccessible()
    }

    @Test
    fun `nothing picked leaves the button off`() {
        setPicker(
            StoryAlbumPreviewData.picking.copy(purpose = StoryAlbumPickPurpose.Remove, selected = persistentSetOf()),
        )

        composeRule.onNodeWithText("Remove from album").assertIsNotEnabled()
    }
}
