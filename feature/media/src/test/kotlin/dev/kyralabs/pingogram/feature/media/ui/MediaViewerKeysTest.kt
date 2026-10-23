package dev.kyralabs.pingogram.feature.media.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMultiModalInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.feature.media.ui.preview.MediaPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The media viewer with a hardware keyboard and a mouse (`docs/DESIGN_SYSTEM.md` §9, `docs/audit/accessibility.md` A3):
 * Left and Right turn the pages (mirrored right to left), +, − and 0 zoom the photo, Ctrl and the wheel zoom it too,
 * Space plays or pauses a video, Esc closes the viewer. The screen is stateless; its events are recorded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
@OptIn(ExperimentalTestApi::class)
class MediaViewerKeysTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<MediaViewerEvent>()
    private var backs = 0

    private fun setScreen(
        state: MediaViewerUiState = MediaPreviewData.photo,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                    MediaViewerScreen(state = state, onEvent = { events += it }, onBack = { backs++ })
                }
            }
        }
        composeRule.waitForIdle()
    }

    private val settled get() = events.filterIsInstance<MediaViewerEvent.PageSettled>().map { it.messageId }

    private fun press(key: Key) {
        composeRule.onRoot().performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }

    private fun photoPage(): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription("Photo, 3 of 120", substring = true))

    private fun isZoomedIn(): Boolean =
        photoPage().fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) ==
            string(R.string.media_zoomed_in)

    @Test
    fun `Right turns to the newer page and Left back`() {
        setScreen()

        press(Key.DirectionRight)
        assertEquals(listOf(1L, 2L), settled)

        press(Key.DirectionLeft)
        assertEquals(listOf(1L, 2L, 1L), settled)
    }

    @Test
    fun `right to left, Left turns to the newer page`() {
        setScreen(layoutDirection = LayoutDirection.Rtl)

        press(Key.DirectionLeft)

        assertEquals(listOf(1L, 2L), settled)
    }

    @Test
    fun `Left on the first page does nothing`() {
        setScreen()

        press(Key.DirectionLeft)

        assertEquals(listOf(1L), settled)
    }

    @Test
    fun `plus zooms the photo in, minus out, and 0 fits it again`() {
        setScreen()

        press(Key.Plus)
        assertTrue("zoomed in", isZoomedIn())

        press(Key.Zero)
        assertTrue("fitted", !isZoomedIn())

        press(Key.Equals)
        assertTrue("zoomed in with =", isZoomedIn())
        press(Key.Minus)
        assertTrue("zoomed out", !isZoomedIn())
    }

    @Test
    fun `Ctrl and the wheel zoom the photo`() {
        setScreen()

        photoPage().performMultiModalInput {
            key { keyDown(Key.CtrlLeft) }
            mouse {
                moveTo(center)
                scroll(-1f)
            }
            key { keyUp(Key.CtrlLeft) }
        }
        composeRule.waitForIdle()

        assertTrue("zoomed in", isZoomedIn())
    }

    @Test
    fun `Space plays or pauses the video`() {
        setScreen(MediaPreviewData.video)

        press(Key.Spacebar)

        assertTrue(MediaViewerEvent.PlayPause in events)
    }

    @Test
    fun `Space on a photo does nothing`() {
        setScreen()

        press(Key.Spacebar)

        assertNull(events.firstOrNull { it == MediaViewerEvent.PlayPause })
    }

    @Test
    fun `Esc closes the viewer`() {
        setScreen()

        press(Key.Escape)

        assertEquals(1, backs)
    }

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)
}
