package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachOption
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiPanelEvent
import dev.kyralabs.pingogram.feature.chat.ui.preview.PanelPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.voice.VoiceRecordingUiState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The emoji and sticker panel from the composer: the button opens it and tells the panel's view model, emoji go into
 * the text and become recent, the backspace deletes, stickers are sent, back and the keyboard button close it, and
 * editing a message shows emoji only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ComposerPanelTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<EmojiPanelEvent>()
    private val stickers = mutableListOf<Sticker>()
    private lateinit var panel: ComposerPanelState

    private fun setComposer(
        text: ComposerText = ComposerText(),
        composer: ComposerUiState.Available =
            ComposerUiState.Available(canSchedule = true, attachOptions = persistentListOf(AttachOption.Gallery)),
        recording: VoiceRecordingUiState? = null,
    ): ComposerText {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                panel = remember { ComposerPanelState() }
                ChatBottomBar(
                    composer = composer,
                    text = text,
                    onSend = {},
                    onFocusChanged = {},
                    recording = recording,
                    panel = PanelPreviewData.content,
                    onPanelEvent = { events += it },
                    onSendSticker = { stickers += it },
                    panelState = panel,
                )
            }
        }
        return text
    }

    private fun openPanel() {
        composeRule.onNodeWithContentDescription(string(R.string.panel_open)).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `the emoji button opens the panel, which starts loading`() {
        setComposer()

        openPanel()

        assertTrue(panel.isOpen)
        assertEquals(listOf(EmojiPanelEvent.Opened), events)
        composeRule.onNodeWithContentDescription(string(R.string.panel_keyboard)).assertExists()
    }

    @Test
    fun `an emoji goes into the text and becomes recent, and the backspace deletes it`() {
        val text = setComposer()
        openPanel()

        composeRule.onNodeWithText("😃").performClick()
        val typed = text.content.text
        composeRule.onNodeWithContentDescription(string(R.string.panel_backspace)).performClick()

        assertEquals("😃", typed)
        assertEquals(EmojiPanelEvent.EmojiPicked("😃"), events.last())
        assertEquals("", text.content.text)
    }

    @Test
    fun `a sticker tapped on the stickers tab is sent`() {
        setComposer()
        openPanel()

        composeRule.onNodeWithContentDescription(string(R.string.panel_tab_stickers)).performClick()
        composeRule.onNodeWithContentDescription(string(UiR.string.preview_sticker_emoji, "🐱")).performClick()

        assertEquals(listOf("🐱"), stickers.map { it.emoji })
    }

    @Test
    fun `while editing, only emoji show`() {
        setComposer(composer = ComposerUiState.Available(canSchedule = true))
        openPanel()

        composeRule.onNodeWithContentDescription(string(R.string.panel_tab_stickers)).assertDoesNotExist()
        composeRule.onNodeWithText("😀").assertExists()
    }

    @Test
    fun `back closes the panel`() {
        setComposer()
        openPanel()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertFalse(panel.isOpen)
        composeRule.onNodeWithContentDescription(string(R.string.panel_open)).assertExists()
    }

    @Test
    fun `the keyboard button closes the panel when no keyboard comes`() {
        setComposer()
        openPanel()

        composeRule.onNodeWithContentDescription(string(R.string.panel_keyboard)).performClick()
        composeRule.mainClock.advanceTimeBy(KEYBOARD_WAIT_MILLIS)
        composeRule.waitForIdle()

        assertFalse(panel.isOpen)
    }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = RuntimeEnvironment.getApplication().getString(id, *args)

    private companion object {
        // More than ComposerPanelEffects waits for a keyboard.
        const val KEYBOARD_WAIT_MILLIS = 1_000L
    }
}
