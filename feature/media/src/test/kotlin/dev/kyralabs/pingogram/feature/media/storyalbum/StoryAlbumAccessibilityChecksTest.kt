package dev.kyralabs.pingogram.feature.media.storyalbum

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
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
 * Story albums have named controls with 48 dp touch targets ([assertAccessible], `docs/audit/accessibility.md`): each
 * cover and each story is one button that names what it opens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StoryAlbumAccessibilityChecksTest {
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
    fun albums() {
        val opened = mutableListOf<Int>()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { StoryAlbumsContent(StoryAlbumPreviewData.albums, { opened += it }) }
        }

        composeRule.assertAccessible()
        composeRule.onNodeWithContentDescription("Monsoon, story album").assertHasClickAction().performClick()
        assertEquals(listOf(3), opened)
    }

    @Test
    fun album() {
        val played = mutableListOf<Int>()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                StoryAlbumScreen(StoryAlbumPreviewData.album, {}, {}, { played += it })
            }
        }

        composeRule.assertAccessible()
        composeRule
            .onNodeWithContentDescription(
                "Video story from Oct 3",
            ).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(2), played)
    }

    @Test
    fun nextPageFailed() = check(StoryAlbumPreviewData.moreFailed)

    @Test
    fun empty() = check(StoryAlbumPreviewData.empty)

    @Test
    fun error() = check(StoryAlbumPreviewData.error)

    private fun check(state: StoryAlbumUiState) {
        composeRule.setContent { PingogramTheme(dynamicColor = false) { StoryAlbumScreen(state, {}, {}, {}) } }
        composeRule.assertAccessible()
    }
}
