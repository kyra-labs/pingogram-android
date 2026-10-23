package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.preview.TranslationPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Message translation on the conversation screen (`REQUIREMENTS.md` FR-C3): "Translate" among a message's actions, a
 * translated bubble's label and its "Show original" for screen readers, and the translate bar with its close as a
 * custom action. The screen is stateless: these check its events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class TranslationScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()

    private fun setScreen(state: ChatUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(state = state, onEvent = { events += it }, onBack = {})
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the actions offer Translate after Copy and send it`() {
        setScreen(TranslationPreviewData.offered.copy(messageActions = TranslationPreviewData.actions))

        composeRule.onNodeWithText("Translate").performClick()
        composeRule.waitForIdle()

        assertEquals(TranslationEvent.Translate(64), events.last())
    }

    @Test
    fun `a translated bubble says what it was translated from, and screen readers can show the original`() {
        setScreen(TranslationPreviewData.translated)

        // The bubble is one item for screen readers: its label, then the translation.
        val bubble =
            composeRule
                .onNodeWithContentDescription("Translated from Spanish, I'll send you the address", substring = true)
                .fetchSemanticsNode()
        val showOriginal = bubble.config[SemanticsActions.CustomActions].single { it.label == "Show original" }
        composeRule.runOnIdle { showOriginal.action() }

        assertEquals(TranslationEvent.ShowOriginal(63), events.last())
    }

    @Test
    fun `a bubble on its way to a translation offers no Show original yet`() {
        setScreen(TranslationPreviewData.translated)

        val labels =
            composeRule
                .onNodeWithContentDescription("Translating…, ¿Quedamos mañana", substring = true)
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .map { it.label }

        assertFalse("Show original" in labels)
    }

    @Test
    fun `the bar offers the chat's translation into English and turns it on`() {
        setScreen(TranslationPreviewData.offered)
        composeRule.onNodeWithText("Chat in Spanish").assertIsDisplayed()

        composeRule.onNodeWithText("Translate to English").performClick()
        composeRule.waitForIdle()

        assertEquals(TranslationEvent.ToggleChat, events.last())
    }

    @Test
    fun `screen readers close the bar through its custom action`() {
        setScreen(TranslationPreviewData.offered)

        val bar =
            composeRule
                .onNode(hasText("Translate to English").and(hasClickAction()))
                .fetchSemanticsNode()
        val close = bar.config[SemanticsActions.CustomActions].single { it.label == "Close translate bar" }
        composeRule.runOnIdle { close.action() }

        assertEquals(TranslationEvent.CloseBar, events.last())
    }

    @Test
    fun `once on, the bar offers the original`() {
        setScreen(TranslationPreviewData.translated)

        composeRule.onNodeWithText("Messages translated by Telegram").assertIsDisplayed()
        composeRule.onNodeWithText("Show original").performClick()
        composeRule.waitForIdle()

        assertTrue(events.last() == TranslationEvent.ToggleChat)
    }
}
