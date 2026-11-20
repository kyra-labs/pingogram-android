package dev.kyralabs.pingogram.feature.media.storyalbum

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.media.ui.COMPACT
import org.junit.Assert.assertEquals
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
 * A profile's Stories tab (owner decision 2026-11-20): album covers with their counts open their album, a story's tile
 * plays the profile's stories from it, the end of the grid asks for more, and the empty and failed states.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ProfileStoriesItemsTest {
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

    private val events = mutableListOf<ProfileStoriesEvent>()
    private val opened = mutableListOf<String>()
    private val actions =
        ProfileStoriesActions({ events += it }, { opened += "story $it" }, { opened += "album $it" })

    private fun setTab(state: ProfileStoriesUiState) =
        composeRule.setContent { PingogramTheme(dynamicColor = false) { ProfileStoriesGrid(state, actions) } }

    @Test
    fun `a cover opens its album and a story plays the profile from it`() {
        setTab(StoryAlbumPreviewData.profileStories)

        composeRule.onNodeWithContentDescription("Hampi trip, story album, 12 stories").performClick()
        composeRule.onNodeWithContentDescription("Story from Oct 3").performClick()

        assertEquals(listOf("album 1", "story 1"), opened)
        composeRule.assertAccessible()
    }

    @Test
    fun `the user's own tab offers New album`() {
        var newAlbum = 0
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ProfileStoriesGrid(
                    StoryAlbumPreviewData.profileStories.copy(canCreateAlbum = true),
                    ProfileStoriesActions({}, {}, {}, onNewAlbum = { newAlbum++ }),
                )
            }
        }

        composeRule.onNodeWithText("New album").performClick()

        assertEquals(1, newAlbum)
        composeRule.assertAccessible()
    }

    @Test
    fun `the end of the grid asks for the next page`() {
        val state = StoryAlbumPreviewData.profileStories.copy(totalCount = 20)
        setTab(state)
        assertTrue(ProfileStoriesEvent.LoadMore !in events)

        // The covers, the stories, then the footer.
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(state.stories.size + 1)
        composeRule.waitForIdle()

        assertEquals(listOf(ProfileStoriesEvent.LoadMore), events)
    }

    @Test
    fun `a failed next page offers Try again`() {
        setTab(StoryAlbumPreviewData.profileStoriesMoreFailed)

        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Try again"))
        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf(ProfileStoriesEvent.Retry), events)
    }

    @Test
    fun `a profile without stories says so`() {
        setTab(ProfileStoriesUiState.Empty)
        composeRule.onNodeWithText("No stories yet").assertIsDisplayed()
    }

    @Test
    fun `stories that failed to load say why and offer Try again`() {
        setTab(ProfileStoriesUiState.Error(DomainError.Network))

        composeRule.onNodeWithText("Couldn't load stories").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf(ProfileStoriesEvent.Retry), events)
        composeRule.assertAccessible()
    }
}
