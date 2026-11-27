package dev.kyralabs.pingogram.feature.chat.ui.privacy

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.ui.privacy.LocalPrivacyScreen
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.LIFTED_MESSAGE_TAG
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A message the privacy screen hides stays hidden in its long press's floating menu (`REQUIREMENTS.md` FR-X25, owner
 * decision 2026-11-27): its lifted copy is blurred as its row is, and the same tap shows both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PrivacyScreenMenuTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(ChatPreviewData.privateChat)

    private fun onEvent(event: ChatEvent) {
        if (event is ChatEvent.ShowMessageActions) {
            state =
                state.copy(
                    messageActions =
                        ChatPreviewData.ownMessageActions.copy(
                            messageId = event.messageId,
                            reactionsMessageId = event.messageId,
                        ),
                )
        }
    }

    private fun setScreen() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalPrivacyScreen provides true) {
                    ChatScreen(state = state, onEvent = ::onEvent, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hiddenMessages() =
        composeRule.onAllNodes(hasContentDescription(HIDDEN, substring = true), useUnmergedTree = true)

    private fun liftFirstHidden(): Int {
        val before = hiddenMessages().fetchSemanticsNodes().size
        hiddenMessages().onFirst().performTouchInput { longClick() }
        composeRule.waitForIdle()
        return before
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun `a long press keeps the message hidden in the menu`() {
        setScreen()
        val before = liftFirstHidden()

        composeRule.onNodeWithTag(LIFTED_MESSAGE_TAG).assertExists()
        assertEquals(before, hiddenMessages().fetchSemanticsNodes().size)
        captureScreenRoboImage(goldenPath(javaClass, "menuLight"))
    }

    @Test
    fun `a tap on the lifted copy shows the message, there and on its row`() {
        setScreen()
        val before = liftFirstHidden()

        composeRule.onNodeWithTag(LIFTED_MESSAGE_TAG).performTouchInput { click() }
        composeRule.waitForIdle()

        assertEquals(before - 1, hiddenMessages().fetchSemanticsNodes().size)
    }

    private companion object {
        const val HIDDEN = "hidden by the privacy screen"
    }
}
