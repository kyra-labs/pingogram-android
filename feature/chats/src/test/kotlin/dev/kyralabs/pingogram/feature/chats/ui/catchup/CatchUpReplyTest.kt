package dev.kyralabs.pingogram.feature.chats.ui.catchup

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Replying in place (the owner's board 32d): "Reply in place" opens the field, "Send and go to next" sends it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CatchUpReplyTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<CatchUpEvent>()
    private var state by mutableStateOf(CatchUpPreviewData.cards)

    private fun setScreen() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CatchUpScreen(
                    state = state,
                    onEvent = { event ->
                        events += event
                        when (event) {
                            CatchUpEvent.Reply -> state = CatchUpPreviewData.replying
                            CatchUpEvent.CancelReply -> state = CatchUpPreviewData.cards
                            else -> Unit
                        }
                    },
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun `reply in place opens the field, which sends what is typed`() {
        setScreen()

        composeRule.onNodeWithContentDescription("Reply in place").performClick()
        composeRule.onNodeWithContentDescription("Send and go to next").assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextInput("On it")
        composeRule.onNodeWithContentDescription("Send and go to next").assertIsEnabled().performClick()

        assertEquals(listOf(CatchUpEvent.Reply, CatchUpEvent.SendReply("On it")), events)
    }

    @Test
    fun `cancel reply closes the field`() {
        setScreen()
        composeRule.onNodeWithContentDescription("Reply in place").performClick()

        composeRule.onNodeWithContentDescription("Cancel reply").performClick()

        assertEquals(listOf(CatchUpEvent.Reply, CatchUpEvent.CancelReply), events)
        composeRule.onNodeWithContentDescription("Reply in place").assertIsEnabled()
    }
}
