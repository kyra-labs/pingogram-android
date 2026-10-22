package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonPosition
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotKeyboard
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotKeyboardButtons
import dev.kyralabs.pingogram.feature.chat.ui.preview.BotButtonsPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The buttons a bot attached under a message ([BotKeyboardButtons]): a tap sends the button's place, each keeps a 48 dp
 * touch target, a button the bot turned off takes no tap, and screen readers reach every button through the bubble's
 * custom actions, as the bubble is read as one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BotKeyboardButtonsTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val pressed = mutableListOf<BotButtonPosition>()
    private val events = mutableListOf<ChatEvent>()

    @Test
    fun `tapping a button sends its row and column`() {
        setKeyboard()

        composeRule.onNodeWithText("🤔 3").performClick()
        composeRule.onNodeWithText("Share poll").performClick()

        assertEquals(listOf(BotButtonPosition(0, 1), BotButtonPosition(1, 1)), pressed)
    }

    @Test
    fun `each button keeps a 48 dp touch target, which the next row's does not overlap`() {
        setKeyboard()

        composeRule.onNode(hasText("Close poll") and hasClickAction()).assertTouchHeightIsEqualTo(48.dp)
        val first = composeRule.onNode(hasText("👍 12") and hasClickAction()).getUnclippedBoundsInRoot()
        val second = composeRule.onNode(hasText("Results") and hasClickAction()).getUnclippedBoundsInRoot()
        assertTrue("${second.top - first.top}", second.top - first.top >= 48.dp)
    }

    @Test
    fun `a button the bot turned off takes no tap`() {
        setKeyboard(BotButtonsPreviewData.settingsKeyboard)

        composeRule.onNodeWithText("‹ Back").assertIsNotEnabled()
    }

    @Test
    fun `screen readers press the buttons through the bubble's custom actions`() {
        setConversation()

        performCustomAction("Next ›")
        performCustomAction(string(R.string.bot_button_link_action, "Results"))

        assertEquals(
            listOf<ChatEvent>(
                BotButtonEvent.Press(2, BotButtonPosition(1, 1)),
                BotButtonEvent.Press(3, BotButtonPosition(1, 0)),
            ),
            events.filterIsInstance<BotButtonEvent>(),
        )
    }

    private fun setKeyboard(keyboard: BotKeyboard = BotButtonsPreviewData.keyboard) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                BotKeyboardButtons(keyboard, onPress = { pressed += it })
            }
        }
    }

    private fun setConversation() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(state = BotButtonsPreviewData.conversation, onEvent = { events += it }, onBack = {})
            }
        }
    }

    /** Does what the custom accessibility action [label] of the one node that offers it does, as TalkBack would. */
    private fun performCustomAction(label: String) {
        val node = composeRule.onNode(hasCustomAction(label)).fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].single { it.label == label }
        composeRule.runOnIdle { action.action() }
    }

    private fun hasCustomAction(label: String) =
        SemanticsMatcher("has the custom action $label") { node ->
            node.config
                .getOrNull(SemanticsActions.CustomActions)
                .orEmpty()
                .any { it.label == label }
        }

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = RuntimeEnvironment.getApplication().getString(id, *args)
}
