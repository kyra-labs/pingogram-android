package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.repository.PlaybackSpeed
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the content components report as the user taps them, and what screen readers can do with them. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MessageContentInteractionTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()

    private fun setContent(content: @Composable () -> Unit) =
        composeRule.setContent { PingogramTheme(dynamicColor = false, content = content) }

    @Test
    fun `tapping a photo opens it`() {
        setContent { MessagePhoto(ContentPreviewData.photo(minithumbnail = null), onOpen = { calls += "open" }) }

        composeRule.onNode(hasContentDescription("Photo") and hasClickAction()).performClick()

        assertEquals(listOf("open"), calls)
    }

    @Test
    fun `tapping an album item opens it by its index`() {
        setContent {
            MediaAlbum(ContentPreviewData.album(count = 3, minithumbnail = null), onOpen = { calls += "open $it" })
        }

        composeRule.onNode(hasContentDescription("Video, 1:07") and hasClickAction()).performClick()

        assertEquals(listOf("open 2"), calls)
    }

    @Test
    fun `the play button plays and pauses a voice message`() {
        setContent { VoiceNoteUnderTest(isPlaying = false) }

        composeRule.onNodeWithContentDescription("Play").performClick()

        assertEquals(listOf("playPause"), calls)
    }

    @Test
    fun `screen readers seek a voice message through its progress`() {
        setContent { VoiceNoteUnderTest(isPlaying = true) }

        composeRule
            .onNode(hasContentDescription("Voice message, 0:00 / 0:42", substring = true))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }

        assertEquals(listOf("seek 0.5"), calls)
    }

    @Test
    fun `the speed chip of a voice message in the player reads its speed and changes it`() {
        setContent { VoiceNoteUnderTest(isPlaying = true, speed = PlaybackSpeed.Faster) }

        composeRule
            .onNode(hasContentDescription("Playback speed, 1.5 times") and hasClickAction())
            .assert(
                SemanticsMatcher("its action is \"Change speed\"") {
                    it.config[SemanticsActions.OnClick].label == "Change speed"
                },
            ).performClick()

        assertEquals(listOf("changeSpeed"), calls)
    }

    @Test
    fun `a round video message in the player has its own speed chip`() {
        setContent {
            MessageVideoNote(
                content = ContentPreviewData.videoNote(minithumbnail = null, isViewed = true),
                onPlayPause = { calls += "playPause" },
                onChangeSpeed = { calls += "changeSpeed" },
                playback = Playback(isPlaying = true, progress = { 0f }),
            )
        }

        composeRule.onNode(hasContentDescription("Playback speed, normal") and hasClickAction()).performClick()

        assertEquals(listOf("changeSpeed"), calls)
    }

    @Test
    fun `a voice message out of the player has no speed chip`() {
        setContent {
            MessageVoiceNote(
                content = ContentPreviewData.voiceNote(isListened = true),
                fileState = null,
                playback = Playback.Stopped,
                onPlayPause = {},
                onSeek = {},
            )
        }

        composeRule.onNode(hasContentDescription("Playback speed", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `a file that is not downloaded can be downloaded`() {
        setContent { DocumentUnderTest(FileState.NotDownloaded(size = 0, canBeDownloaded = true)) }

        composeRule.onNodeWithContentDescription("Download").performClick()

        assertEquals(listOf("download"), calls)
    }

    @Test
    fun `a download can be paused`() {
        setContent { DocumentUnderTest(FileState.Downloading(downloadedSize = 1, size = 4, isActive = true)) }

        composeRule.onNodeWithContentDescription("Pause download").performClick()

        assertEquals(listOf("cancel"), calls)
    }

    @Test
    fun `a downloaded file opens when tapped`() {
        setContent { DocumentUnderTest(FileState.Downloaded(path = "", size = 0)) }

        composeRule.onNode(hasText("report.pdf") and hasClickAction()).performClick()

        assertEquals(listOf("open"), calls)
    }

    @Composable
    private fun VoiceNoteUnderTest(
        isPlaying: Boolean,
        speed: PlaybackSpeed = PlaybackSpeed.Normal,
    ) {
        MessageVoiceNote(
            content = ContentPreviewData.voiceNote(isListened = true),
            fileState = null,
            playback = Playback(isPlaying = isPlaying, progress = { 0f }, speed = speed),
            onPlayPause = { calls += "playPause" },
            onSeek = { calls += "seek $it" },
            onChangeSpeed = { calls += "changeSpeed" },
        )
    }

    @Composable
    private fun DocumentUnderTest(fileState: FileState) {
        MessageDocument(
            content = ContentPreviewData.document("report.pdf", "application/pdf"),
            fileState = fileState,
            onDownload = { calls += "download" },
            onCancel = { calls += "cancel" },
            onOpen = { calls += "open" },
        )
    }
}
