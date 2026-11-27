package dev.kyralabs.pingogram.feature.chat.ui.privacy

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.privacy.LocalPrivacyScreen
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The privacy screen in a conversation (`REQUIREMENTS.md` FR-X25): messages with text or photos stay hidden, from
 * screen readers too; a tap shows one and does nothing else; off, nothing is hidden.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PrivacyScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()

    private fun setScreen(privacyScreen: Boolean) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalPrivacyScreen provides privacyScreen) {
                    ChatScreen(state = ChatPreviewData.groupChat, onEvent = { events += it }, onBack = {})
                }
            }
        }
    }

    private fun hiddenMessages() =
        composeRule.onAllNodes(hasContentDescription(HIDDEN, substring = true), useUnmergedTree = true)

    @Test
    fun `while on, messages are hidden from screen readers too, and every control can be reached`() {
        setScreen(privacyScreen = true)

        assertTrue(hiddenMessages().fetchSemanticsNodes().isNotEmpty())
        composeRule.assertAccessible()
    }

    @Test
    fun `a tap shows a message and does nothing else`() {
        setScreen(privacyScreen = true)
        val before = hiddenMessages().fetchSemanticsNodes().size

        hiddenMessages().onFirst().performClick()
        composeRule.waitForIdle()

        assertEquals(before - 1, hiddenMessages().fetchSemanticsNodes().size)
        assertEquals(emptyList<ChatEvent>(), events)
    }

    @Test
    fun `screen readers show one with its action`() {
        setScreen(privacyScreen = true)
        val before = hiddenMessages().fetchSemanticsNodes().size

        val actions = hiddenMessages().onFirst().fetchSemanticsNode().config[SemanticsActions.CustomActions]
        composeRule.runOnIdle { actions.first { it.label == SHOW }.action() }
        composeRule.waitForIdle()

        assertEquals(before - 1, hiddenMessages().fetchSemanticsNodes().size)
    }

    @Test
    fun `off, nothing is hidden`() {
        setScreen(privacyScreen = false)

        assertTrue(hiddenMessages().fetchSemanticsNodes().isEmpty())
    }

    private companion object {
        const val HIDDEN = "hidden by the privacy screen"
        const val SHOW = "Show"
    }
}
