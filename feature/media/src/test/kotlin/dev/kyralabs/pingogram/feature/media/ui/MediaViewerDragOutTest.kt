package dev.kyralabs.pingogram.feature.media.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.file.DraggedFile
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.media.ui.preview.MediaPreviewData
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Dragging the shown photo, video or GIF out of the viewer into another app (the owner's board 35c): a long press, then
 * a move, hands the downloaded file to the drag; nothing for a quick swipe, a file not on the device, or content
 * Telegram keeps in the chat. Android's drag itself is a seam ([MediaDragOut]'s `drag`), recorded here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MediaViewerDragOutTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val dragged = mutableListOf<Pair<DraggedFile, String>>()
    private var started = 0

    private val dragOut =
        MediaDragOut(
            label = { media -> dragLabel(RuntimeEnvironment.getApplication().resources, media) },
            drag = { file, label ->
                dragged += file to label
                true
            },
            onStarted = { started++ },
        )

    private fun setScreen(state: MediaViewerUiState) {
        composeRule.setContent {
            PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                MediaViewerScreen(state = state, onEvent = {}, onBack = {}, dragOut = dragOut)
            }
        }
    }

    private fun photoPage(): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription("Photo, 3 of 120, Ada Lovelace", substring = true))

    private fun TouchInjectionScope.longPressThenMove() {
        down(center)
        advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
        moveBy(center.copy(x = 0f, y = viewConfiguration.touchSlop * 3))
        up()
    }

    @Test
    fun `a long press then a move drags the downloaded photo out`() {
        setScreen(MediaPreviewData.withActions)

        photoPage().performTouchInput { longPressThenMove() }
        composeRule.waitForIdle()

        assertEquals(listOf(DraggedFile("/files/11.jpg", "image/jpeg") to "Photo"), dragged)
        assertEquals(1, started)
    }

    @Test
    fun `a quick swipe never drags`() {
        setScreen(MediaPreviewData.withActions)

        photoPage().performTouchInput {
            down(center)
            moveBy(center.copy(x = 0f, y = viewConfiguration.touchSlop * 3))
            up()
        }
        composeRule.waitForIdle()

        assertEquals(emptyList<Pair<DraggedFile, String>>(), dragged)
    }

    @Test
    fun `content Telegram keeps in the chat does not drag`() {
        setScreen(MediaPreviewData.photo)

        photoPage().performTouchInput { longPressThenMove() }
        composeRule.waitForIdle()

        assertEquals(emptyList<Pair<DraggedFile, String>>(), dragged)
    }

    @Test
    fun `a file not on the device yet does not drag`() {
        setScreen(
            MediaPreviewData.withActions.copy(
                files = persistentMapOf(11 to FileState.Downloading(96_000, 240_000, isActive = true)),
            ),
        )

        photoPage().performTouchInput { longPressThenMove() }
        composeRule.waitForIdle()

        assertEquals(emptyList<Pair<DraggedFile, String>>(), dragged)
        assertEquals(0, started)
    }

    @Test
    fun `the label says what is dragged`() {
        val resources = RuntimeEnvironment.getApplication().resources
        val video = (MediaPreviewData.video.current.media as ViewerMedia.Clip)

        assertEquals("Video", dragLabel(resources, video))
    }
}
