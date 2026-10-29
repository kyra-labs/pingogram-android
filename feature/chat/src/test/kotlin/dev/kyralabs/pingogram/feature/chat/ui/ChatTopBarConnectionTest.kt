package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The conversation's top bar while TDLib is not connected (audit A11): "Connecting…" and the like take the status's
 * place, and the title, a polite live region meanwhile, is announced as it changes; connected, the status is back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatTopBarConnectionTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var connection by mutableStateOf(ConnectionStatus.Ready)

    @Test
    fun `while connecting, the state replaces the status and is announced`() {
        setTopBar(ConnectionStatus.Connecting)

        composeRule.onNodeWithText("Connecting…", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("online", useUnmergedTree = true).assertDoesNotExist()
        composeRule
            .onNode(isHeading())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun `each state says what TDLib is doing`() {
        setTopBar(ConnectionStatus.WaitingForNetwork)
        composeRule.onNodeWithText("Waiting for network…", useUnmergedTree = true).assertExists()

        connection = ConnectionStatus.Updating
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Updating…", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `connected, the status is back and the title is no live region`() {
        setTopBar(ConnectionStatus.Connecting)

        connection = ConnectionStatus.Ready
        composeRule.waitForIdle()

        composeRule.onNodeWithText("online", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Connecting…", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNode(isHeading()).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }

    private fun setTopBar(initial: ConnectionStatus) {
        connection = initial
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatTopBar(header = ChatPreviewData.privateChat.header, onBack = {}, connection = connection)
            }
        }
        composeRule.waitForIdle()
    }
}
