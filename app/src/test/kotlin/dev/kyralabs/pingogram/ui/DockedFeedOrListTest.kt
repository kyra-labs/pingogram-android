package dev.kyralabs.pingogram.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Chats tab's list pane with the Channel feed docked (`docs/DESIGN_SYSTEM.md` §6 "Channel feed", frame 7b): the
 * feed in the list's place while docked, and system back closing it, unless something beside it takes back first.
 */
@RunWith(RobolectricTestRunner::class)
class DockedFeedOrListTest {
    @get:Rule(order = 0)
    val activityRegistration = ActivityRegistration(ComponentActivity::class.java)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var docked by mutableStateOf(true)
    private var conversationBacks = 0

    private fun setPane(backEnabled: Boolean = true) =
        composeRule.setContent {
            // The conversation beside the feed, which takes back while the feed's is off.
            BackHandler(enabled = !backEnabled) { conversationBacks++ }
            DockedFeedOrList(
                docked = docked,
                backEnabled = backEnabled,
                onClose = { docked = false },
                feed = { Text("Feed") },
                list = { Text("Chats") },
            )
        }

    private fun pressBack() = composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun `the docked feed takes the list's place, and back returns to the chats`() {
        setPane()
        composeRule.onNodeWithText("Feed").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Chats").fetchSemanticsNodes().size)

        pressBack()

        composeRule.onNodeWithText("Chats").assertIsDisplayed()
    }

    @Test
    fun `back goes elsewhere first while a conversation beside it takes it`() {
        setPane(backEnabled = false)

        pressBack()

        composeRule.onNodeWithText("Feed").assertIsDisplayed()
        assertEquals(1, conversationBacks)
    }
}
