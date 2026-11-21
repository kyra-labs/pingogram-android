package dev.kyralabs.pingogram.feature.media.storyalbum

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * The user's own album's menu (owner decision 2026-11-20): deleting after asking, picking stories to add or remove;
 * someone else's album has no menu. Renaming is covered by the view model's tests and the name dialog's golden: under
 * Robolectric, a text field in a dialog's window never lets Compose go idle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StoryAlbumEditingTest {
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

    private val events = mutableListOf<StoryAlbumEvent>()
    private val picked = mutableListOf<Boolean>()

    private fun setAlbum(canEdit: Boolean) =
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                StoryAlbumScreen(
                    state = StoryAlbumPreviewData.album.copy(canEdit = canEdit),
                    onEvent = { events += it },
                    onBack = {},
                    onOpenStory = {},
                    onPickStories = { picked += it },
                )
            }
        }

    private fun choose(action: String) {
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText(action).performClick()
    }

    @Test
    fun `the menu deletes the album after asking`() {
        setAlbum(canEdit = true)

        choose("Delete album")
        composeRule.onNodeWithText("Delete this album?").assertExists()
        composeRule.assertAccessible()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(listOf(StoryAlbumEvent.Delete), events.filter { it == StoryAlbumEvent.Delete })
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `the menu picks stories to add or to remove`() {
        setAlbum(canEdit = true)

        choose("Add stories")
        choose("Remove stories")

        assertEquals(listOf(false, true), picked)
    }

    @Test
    fun `someone else's album has no menu`() {
        setAlbum(canEdit = false)

        composeRule.onNodeWithContentDescription("More options").assertDoesNotExist()
    }
}
