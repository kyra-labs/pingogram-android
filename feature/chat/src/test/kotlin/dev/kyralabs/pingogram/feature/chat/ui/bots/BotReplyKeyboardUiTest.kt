package dev.kyralabs.pingogram.feature.chat.ui.bots

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.composer.BotInput
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatBottomBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerPanelState
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.composer.PanelContent
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineBotInput
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineBotUiState
import dev.kyralabs.pingogram.feature.chat.ui.preview.BotKeyboardPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.InlinePreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A bot's keyboard in the composer in use: it comes up in the system keyboard's place, the toggle switches between
 * the two, one-time and persistent keyboards follow their rules, back puts it away, an inline query's results take
 * precedence, the field shows its hint, and screen readers reach it as a pane of buttons.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BotReplyKeyboardUiTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val pressed = mutableListOf<BotButtonPosition>()
    private lateinit var panel: ComposerPanelState
    private var composer by mutableStateOf<ComposerUiState.Available>(ComposerUiState.Available(canSchedule = true))
    private var inline by mutableStateOf<InlineBotUiState?>(null)

    private fun setComposer(
        keyboard: ReplyKeyboardUiState? = BotKeyboardPreviewData.keyboard,
        placeholder: String = "",
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                panel = remember { ComposerPanelState() }
                ChatBottomBar(
                    composer = composer,
                    text = remember { ComposerText() },
                    onSend = {},
                    onFocusChanged = {},
                    panelState = panel,
                    bots =
                        BotInput(
                            reply = BotReplyUiState(keyboard, placeholder),
                            onKeyboardPress = { pressed += it },
                        ),
                    inline = InlineBotInput(inline),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private val showsKeyboard get() = panel.shows(PanelContent.BotKeyboard)

    private fun toggle(
        @StringRes label: Int,
    ) {
        composeRule.onNodeWithContentDescription(string(label)).performClick()
        composeRule.mainClock.advanceTimeBy(KEYBOARD_WAIT_MILLIS)
        composeRule.waitForIdle()
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun `the keyboard comes up in the system keyboard's place, and a tap sends its button`() {
        setComposer()

        assertTrue(showsKeyboard)
        composeRule.onNodeWithText("Medium").performClick()

        assertEquals(listOf(BotButtonPosition(0, 1)), pressed)
        assertTrue(showsKeyboard)
    }

    @Test
    fun `the toggle switches to the system keyboard and back`() {
        setComposer()

        toggle(R.string.bot_keyboard_hide)
        assertFalse(showsKeyboard)
        composeRule.onNodeWithText("Medium").assertDoesNotExist()

        toggle(R.string.bot_keyboard_show)
        assertTrue(showsKeyboard)
    }

    @Test
    fun `back puts a keyboard away until the toggle brings it back`() {
        setComposer()

        back()
        assertFalse(showsKeyboard)

        toggle(R.string.bot_keyboard_show)
        assertTrue(showsKeyboard)
    }

    @Test
    fun `a one-time keyboard puts itself away after a tap`() {
        setComposer(BotKeyboardPreviewData.resizedKeyboard)

        composeRule.onNodeWithText("No").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(BotButtonPosition(0, 1)), pressed)
        assertFalse(showsKeyboard)
        toggle(R.string.bot_keyboard_show)
        assertTrue(showsKeyboard)
    }

    @Test
    fun `a persistent keyboard comes back whenever the system keyboard is down, and back passes it by`() {
        setComposer(BotKeyboardPreviewData.keyboard.copy(persistent = true))

        toggle(R.string.bot_keyboard_hide)
        assertTrue(showsKeyboard)

        back()
        assertTrue(showsKeyboard)
    }

    @Test
    fun `the emoji panel takes its place, and then it stays away`() {
        setComposer()

        composeRule.onNodeWithContentDescription(string(R.string.panel_open)).performClick()
        composeRule.waitForIdle()
        assertTrue(panel.shows(PanelContent.Emoji))

        back()
        assertFalse(panel.isOpen)
    }

    @Test
    fun `not while editing, and the field shows the keyboard's hint`() {
        setComposer(placeholder = "Pick a size")
        composeRule.onNodeWithText("Pick a size").assertExists()

        composer = composer.copy(bar = ComposerBar.Edit(1, UiText.Plain("Hi"), canBeEmpty = false))
        composeRule.waitForIdle()

        assertFalse(showsKeyboard)
        composeRule.onNodeWithContentDescription(string(R.string.bot_keyboard_show)).assertDoesNotExist()
    }

    @Test
    fun `an inline query's results win, and the keyboard comes back after`() {
        setComposer()

        inline = InlinePreviewData.gifResults
        composeRule.waitForIdle()
        assertFalse(showsKeyboard)
        composeRule.onNodeWithContentDescription(string(R.string.bot_keyboard_show)).assertDoesNotExist()

        inline = null
        composeRule.waitForIdle()
        assertTrue(showsKeyboard)
    }

    @Test
    fun `no keyboard, no toggle`() {
        setComposer(keyboard = null)

        assertFalse(panel.isOpen)
        composeRule.onNodeWithContentDescription(string(R.string.bot_keyboard_show)).assertDoesNotExist()
    }

    @Test
    fun `screen readers reach it as a pane of buttons`() {
        setComposer()

        composeRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, string(R.string.bot_keyboard_pane)))
            .assertExists()
        composeRule.onNodeWithText("Small").assertHasClickAction()
        composeRule.onNodeWithContentDescription(string(R.string.bot_keyboard_hide)).assertHasClickAction()
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        // More than ComposerPanelEffects waits for a keyboard.
        const val KEYBOARD_WAIT_MILLIS = 1_000L
    }
}
