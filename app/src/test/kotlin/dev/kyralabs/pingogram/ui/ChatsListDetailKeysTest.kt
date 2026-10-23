package dev.kyralabs.pingogram.ui

import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.ui.ChatsScript.ADA
import dev.kyralabs.pingogram.ui.ChatsScript.ADA_TITLE
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.feature.chat.R as ChatR
import dev.kyralabs.pingogram.feature.chats.R as ChatsR

/**
 * The Chats tab's list-detail layout with a hardware keyboard (`docs/DESIGN_SYSTEM.md` §9, [PaneKeyboard]): a chat
 * opened from the list with Enter gives its composer the focus, Esc in the conversation gives it back to the list
 * without closing the chat, and a chat opened by touch leaves the focus alone. The real view models and repositories
 * run on a [FakeTdlibClient] with the chats of [ChatsScript], as in [ChatsListDetailTest].
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w840dp-h891dp-xxhdpi")
class ChatsListDetailKeysTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRegistration = ActivityRegistration(HiltTestActivity::class.java)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private val tdlib =
        FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready)
            .also(ChatsScript::install)

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient = tdlib

    private lateinit var inputModes: InputModeManager

    @Test
    fun `Enter on a chat opens it beside the list and gives its composer the focus`() {
        setApp()
        focusRow()

        press(Key.Enter)

        awaitMessage()
        awaitFocused(hasSetTextAction())
        row().assertIsNotFocused()
    }

    @Test
    fun `Esc in the conversation gives the focus back to the chat's row and keeps the chat open`() {
        setApp()
        focusRow()
        press(Key.Enter)
        awaitMessage()
        awaitFocused(hasSetTextAction())

        press(Key.Escape)

        row().assertIsFocused()
        message().assertIsDisplayed()
    }

    @Test
    fun `a chat opened by touch leaves the composer alone`() {
        setApp()
        awaitNode(rowMatcher())

        row().performClick()
        awaitMessage()
        composeRule.waitForIdle()

        composeRule.onNode(hasSetTextAction()).assertIsNotFocused()
    }

    @Test
    fun `Tab moves from the list pane on to the conversation's top bar`() {
        setApp()
        focusRow()
        press(Key.Enter)
        awaitMessage()
        composeRule.onNode(hasContentDescription(string(ChatsR.string.new_chat))).requestFocus()

        press(Key.Tab)

        composeRule.onNode(isFocused()).assert(hasClickLabel(string(ChatR.string.chat_open_profile)))
    }

    private fun setApp() {
        composeRule.setContent {
            inputModes = LocalInputModeManager.current
            PingogramTheme(dynamicColor = false) {
                PingogramApp(uiState = MainActivityUiState.SignedIn, navController = rememberNavController())
            }
        }
    }

    /** Gives Ada's row the focus with the keyboard's input mode, as Tab would. */
    private fun focusRow() {
        awaitNode(rowMatcher())
        composeRule.runOnIdle { assertTrue(inputModes.requestInputMode(InputMode.Keyboard)) }
        row().requestFocus()
        composeRule.waitForIdle()
        row().assertIsFocused()
    }

    private fun press(key: Key) {
        composeRule.onRoot().performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }

    private fun isFocused() =
        SemanticsMatcher("is focused") { it.config.getOrNull(SemanticsProperties.Focused) == true }

    private fun hasClickLabel(label: String) =
        SemanticsMatcher("click label $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun rowMatcher() = hasContentDescription(ADA_TITLE, substring = true) and hasClickAction()

    private fun row(): SemanticsNodeInteraction = composeRule.onNode(rowMatcher())

    private fun message(): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription(ChatsScript.olderMessage.getValue(ADA), substring = true))

    private fun awaitMessage() =
        awaitNode(hasContentDescription(ChatsScript.olderMessage.getValue(ADA), substring = true))

    private fun awaitFocused(matcher: SemanticsMatcher) = awaitNode(matcher and isFocused())

    /** Waits for [matcher]: the screens get their state from a background dispatcher. */
    private fun awaitNode(matcher: SemanticsMatcher) {
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNode(matcher).assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
