package dev.kyralabs.pingogram.feature.media.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.media.story.StoryViewerPreviewData
import dev.kyralabs.pingogram.feature.media.story.StoryViewerScreen
import dev.kyralabs.pingogram.feature.media.story.StoryViewerUiState
import dev.kyralabs.pingogram.feature.media.ui.preview.MediaPreviewData
import dev.kyralabs.pingogram.feature.media.ui.preview.PreviewImages
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The media viewer's and the stories viewer's states have named controls with 48 dp touch targets
 * ([assertAccessible], `docs/audit/accessibility.md`), on a compact phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MediaAccessibilityChecksTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun photo() = viewer(MediaPreviewData.allActions)

    @Test
    fun video() = viewer(MediaPreviewData.video)

    @Test
    fun videoWaitingForTap() = viewer(MediaPreviewData.videoWaitingForTap)

    @Test
    fun deleteConfirmation() = viewer(MediaPreviewData.deleteConfirmation)

    @Test
    fun error() = viewer(MediaPreviewData.error)

    @Test
    fun storyPhoto() = stories(StoryViewerPreviewData.photo)

    @Test
    fun storyVideo() = stories(StoryViewerPreviewData.video)

    @Test
    fun storyReacting() = stories(StoryViewerPreviewData.reacting)

    @Test
    fun storyFailed() = stories(StoryViewerPreviewData.failed)

    private fun viewer(state: MediaViewerUiState) =
        check { MediaViewerScreen(state = state, onEvent = {}, onBack = {}) }

    private fun stories(state: StoryViewerUiState) =
        check { StoryViewerScreen(state = state, onEvent = {}, onClose = {}, replyText = TextFieldState()) }

    private fun check(screen: @Composable () -> Unit) {
        composeRule.setContent {
            // Inspection mode: Coil shows solid colors instead of loading TDLib's files.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PingogramTheme(dynamicColor = false) { PreviewImages(loading = false) { screen() } }
            }
        }
        composeRule.waitForIdle()
        composeRule.assertAccessible()
    }
}
