package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Screen readers hear "Connected" once the connection is back after the chat list's pill showed a state
 * (`docs/DESIGN_SYSTEM.md` §9, audit A11): a polite live region that holds the word for a moment, and says nothing
 * while the connection was ready all along.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ConnectedAnnouncementTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var connection by mutableStateOf(ConnectionStatus.Ready)

    @Test
    fun `connected from the start, nothing is announced`() {
        setScreen(ConnectionStatus.Ready)

        composeRule.mainClock.advanceTimeBy(CONNECTED_ANNOUNCEMENT_MILLIS / 2)
        connectedNodes().assertCountEquals(0)
    }

    @Test
    fun `back from connecting, Connected is announced for a moment`() {
        setScreen(ConnectionStatus.Connecting)
        connectedNodes().assertCountEquals(0)

        connection = ConnectionStatus.Ready
        frames()

        connectedNodes().assertCountEquals(1)
        composeRule
            .onNode(hasContentDescription("Connected"), useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

        composeRule.mainClock.advanceTimeBy(CONNECTED_ANNOUNCEMENT_MILLIS + FRAME_MILLIS)
        connectedNodes().assertCountEquals(0)
    }

    @Test
    fun `losing the connection again takes Connected back at once`() {
        setScreen(ConnectionStatus.Updating)
        connection = ConnectionStatus.Ready
        frames()
        connectedNodes().assertCountEquals(1)

        connection = ConnectionStatus.WaitingForNetwork
        frames()

        connectedNodes().assertCountEquals(0)
    }

    /** A few frames: the change composes, its effect runs, and what it set composes. */
    private fun frames() = composeRule.mainClock.advanceTimeBy(FRAME_MILLIS * 3)

    private fun connectedNodes() = composeRule.onAllNodes(hasContentDescription("Connected"), useUnmergedTree = true)

    private fun setScreen(initial: ConnectionStatus) {
        connection = initial
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatListScreen(state = ChatListPreviewData.content.copy(connection = connection), onEvent = {})
            }
        }
        frames()
    }

    private companion object {
        const val FRAME_MILLIS = 16L
    }
}
