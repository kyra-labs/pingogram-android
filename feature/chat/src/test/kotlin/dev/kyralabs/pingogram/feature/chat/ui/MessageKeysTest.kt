package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.SelectionPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.search.ChatSearchUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The conversation with a hardware keyboard and a mouse (`docs/DESIGN_SYSTEM.md` §9, `docs/audit/accessibility.md` A1,
 * A4): a focused message opens its actions in the floating menu with Enter, the Menu key or Shift+F10, a right click
 * opens them too, and Esc closes the menu and gives the focus back to the message; Ctrl+F opens the search, Esc closes
 * it and leaves selection mode; a sender's name and avatar are buttons that open the profile. The screen answers
 * [ChatEvent.ShowMessageActions] with an actions sheet, as the view model does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
@OptIn(ExperimentalTestApi::class)
class MessageKeysTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()

    private var state by mutableStateOf(ChatPreviewData.privateChat)
    private var search by mutableStateOf<ChatSearchUiState?>(null)

    private lateinit var inputModes: InputModeManager

    private fun setScreen(layoutDirection: LayoutDirection = LayoutDirection.Ltr) {
        composeRule.setContent {
            inputModes = LocalInputModeManager.current
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    ChatScreen(state = state, onEvent = ::onEvent, onBack = {}, search = search)
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** As after a hardware keyboard's first key: the focus moves where the keyboard moves it, not by touch. */
    private fun useKeyboard() {
        composeRule.runOnIdle { assertTrue(inputModes.requestInputMode(InputMode.Keyboard)) }
        composeRule.waitForIdle()
    }

    private fun onEvent(event: ChatEvent) {
        events += event
        when (event) {
            is ChatEvent.ShowMessageActions -> {
                state =
                    state.copy(
                        messageActions =
                            ChatPreviewData.ownMessageActions.copy(
                                messageId = event.messageId,
                                reactionsMessageId = event.messageId,
                            ),
                    )
            }

            ChatEvent.DismissMessageActions, is ChatEvent.Copy -> {
                state = state.copy(messageActions = null)
            }

            ChatSearchEvent.Open -> {
                search = ChatSearchUiState()
            }

            ChatSearchEvent.Close -> {
                search = null
            }

            else -> {
                Unit
            }
        }
    }

    private fun message(): SemanticsNodeInteraction = composeRule.onNodeWithContentDescription(ADA_LAST)

    /** Gives Ada's newest message the keyboard's focus, as Tab would. */
    private fun focusMessage() {
        useKeyboard()
        message().requestFocus()
        composeRule.waitForIdle()
        message().assertIsFocused()
    }

    private fun press(key: Key) {
        composeRule.onRoot().performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }

    private fun assertMenuOpen() {
        composeRule.onNodeWithTag(LIFTED_MESSAGE_TAG).assertExists()
        assertEquals(listOf(ChatEvent.ShowMessageActions(11)), events.filterIsInstance<ChatEvent.ShowMessageActions>())
    }

    // Message actions

    @Test
    fun `Enter on a focused message lifts it with its menu, which takes the focus`() {
        setScreen()
        focusMessage()

        press(Key.Enter)

        assertMenuOpen()
        composeRule.onNodeWithContentDescription(string(R.string.message_reaction_add, "👍")).assertIsFocused()
    }

    @Test
    fun `Esc closes the menu and gives the focus back to the message`() {
        setScreen()
        focusMessage()
        press(Key.Enter)

        press(Key.Escape)

        assertEquals(ChatEvent.DismissMessageActions, events.last())
        composeRule.onNodeWithTag(LIFTED_MESSAGE_TAG).assertDoesNotExist()
        message().assertIsFocused()
    }

    @Test
    fun `an action picked with the keyboard goes to the message`() {
        setScreen()
        focusMessage()
        press(Key.Enter)

        composeRule.onNode(hasText(string(R.string.message_action_copy)) and hasClickAction()).performClick()
        composeRule.waitForIdle()

        assertEquals(ChatEvent.Copy(11), events.last())
        composeRule.onNodeWithTag(LIFTED_MESSAGE_TAG).assertDoesNotExist()
    }

    @Test
    fun `the Menu key opens the actions`() {
        setScreen()
        focusMessage()

        press(Key.Menu)

        assertMenuOpen()
    }

    @Test
    fun `Shift+F10 opens the actions`() {
        setScreen()
        focusMessage()

        composeRule.onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.F10) } }
        composeRule.waitForIdle()

        assertMenuOpen()
    }

    @Test
    fun `F10 alone opens nothing`() {
        setScreen()
        focusMessage()

        press(Key.F10)

        composeRule.onNodeWithTag(LIFTED_MESSAGE_TAG).assertDoesNotExist()
    }

    @Test
    fun `a right click lifts the message, and Esc closes the menu`() {
        setScreen()

        message().performMouseInput { rightClick() }
        composeRule.waitForIdle()
        assertMenuOpen()

        press(Key.Escape)

        assertEquals(ChatEvent.DismissMessageActions, events.last())
        composeRule.onNodeWithTag(LIFTED_MESSAGE_TAG).assertDoesNotExist()
    }

    @Test
    fun `right to left, a right click opens the actions too`() {
        setScreen(LayoutDirection.Rtl)

        message().performMouseInput { rightClick() }
        composeRule.waitForIdle()

        assertMenuOpen()
    }

    @Test
    fun `in selection mode, Enter selects the focused message`() {
        state = ChatPreviewData.privateChat.copy(selection = SelectionPreviewData.selecting.selection)
        setScreen()
        focusMessage()

        press(Key.Enter)

        assertEquals(listOf(SelectionEvent.Toggle(11)), events.filterIsInstance<SelectionEvent.Toggle>())
        composeRule.onNodeWithTag(LIFTED_MESSAGE_TAG).assertDoesNotExist()
    }

    // The conversation's keys

    @Test
    fun `Ctrl+F opens the search, and Esc closes it`() {
        setScreen()
        focusMessage()

        composeRule.onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.F) } }
        composeRule.waitForIdle()
        assertEquals(listOf<ChatEvent>(ChatSearchEvent.Open), events.filterIsInstance<ChatSearchEvent>())

        message().requestFocus()
        press(Key.Escape)

        assertEquals(ChatSearchEvent.Close, events.last())
    }

    @Test
    fun `Esc leaves selection mode`() {
        state = ChatPreviewData.privateChat.copy(selection = SelectionPreviewData.selecting.selection)
        setScreen()
        focusMessage()

        press(Key.Escape)

        assertEquals(SelectionEvent.Clear, events.last())
    }

    // Senders

    @Test
    fun `a sender's name is a button that opens their profile`() {
        val bubble = groupBubble()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                BubbleHeader(bubble, rememberBubbleTheme().incoming.colors, ::onEvent)
            }
        }

        val name = composeRule.onNode(hasText("Linus Torvalds"))
        name.assert(hasRole(Role.Button)).assert(hasClickLabel(string(R.string.chat_open_profile)))
        name.performClick()

        assertEquals(listOf(ChatEvent.OpenProfile(LINUS)), events)
    }

    @Test
    fun `a sender's avatar is a button that opens their profile`() {
        val bubble = groupBubble()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                Row { SenderAvatarColumn(bubble.avatar, bubble.senderUserId, ::onEvent) }
            }
        }

        val avatar = composeRule.onNode(hasRole(Role.Button))
        avatar.assert(hasClickLabel(string(R.string.chat_open_profile)))
        avatar.performClick()

        assertEquals(listOf(ChatEvent.OpenProfile(LINUS)), events)
        assertTrue("focusable", avatar.fetchSemanticsNode().config.contains(SemanticsActions.RequestFocus))
    }

    private fun groupBubble(): ChatItem.Bubble =
        ChatPreviewData
            .bubble(
                25,
                "Hi",
                time = "10:20 AM",
                sender = UiText.Plain("Linus Torvalds"),
                senderId = LINUS,
                groupChat = true,
            ).copy(senderUserId = LINUS)

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun hasClickLabel(label: String) =
        SemanticsMatcher("click label $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = RuntimeEnvironment.getApplication().getString(id, *args)

    private companion object {
        /** Ada's newest message in [ChatPreviewData.privateChat], as screen readers hear it. */
        const val ADA_LAST = "Ada, 9:46 AM: See you there!"

        const val LINUS = 7L
    }
}
