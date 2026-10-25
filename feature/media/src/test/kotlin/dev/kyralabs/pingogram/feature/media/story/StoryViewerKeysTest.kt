package dev.kyralabs.pingogram.feature.media.story

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.media.ui.COMPACT
import dev.kyralabs.pingogram.feature.media.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The stories viewer with a hardware keyboard (`docs/DESIGN_SYSTEM.md` §9): Right and Left show the next and previous
 * story (mirrored right to left), Space pauses and resumes, Esc closes the viewer. Its events are recorded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StoryViewerKeysTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<StoryViewerEvent>()
    private var closes = 0

    private fun setScreen(
        state: StoryViewerUiState = StoryViewerPreviewData.photo,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                    StoryViewerScreen(
                        state = state,
                        onEvent = { events += it },
                        onClose = { closes++ },
                        replyText = TextFieldState(),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun press(key: Key) {
        composeRule.onRoot().performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }

    private val steps get() = events.filter { it is StoryViewerEvent.Next || it is StoryViewerEvent.Previous }

    @Test
    fun `Right shows the next story and Left the previous one`() {
        setScreen()

        press(Key.DirectionRight)
        press(Key.DirectionLeft)

        assertEquals(listOf(StoryViewerEvent.Next, StoryViewerEvent.Previous), steps)
    }

    @Test
    fun `right to left, Left shows the next story`() {
        setScreen(layoutDirection = LayoutDirection.Rtl)

        press(Key.DirectionLeft)

        assertEquals(listOf(StoryViewerEvent.Next), steps)
    }

    @Test
    fun `Space pauses a playing story`() {
        setScreen()

        press(Key.Spacebar)

        assertEquals(listOf(StoryViewerEvent.Hold(true)), events.filterIsInstance<StoryViewerEvent.Hold>())
    }

    @Test
    fun `Space resumes a paused story`() {
        setScreen(StoryViewerPreviewData.photo.copy(isPaused = true))

        press(Key.Spacebar)

        assertEquals(listOf(StoryViewerEvent.Hold(false)), events.filterIsInstance<StoryViewerEvent.Hold>())
    }

    @Test
    fun `Esc closes the viewer`() {
        setScreen()

        press(Key.Escape)

        assertEquals(1, closes)
    }

    @Test
    fun `a volume key gives a muted video its sound back`() {
        setScreen()

        press(Key.VolumeDown)

        assertEquals(listOf(StoryViewerEvent.Unmute), events.filter { it == StoryViewerEvent.Unmute })
    }
}
