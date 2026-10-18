package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What the conversation screen sends and where it opens: back, retry, the oldest message coming near, the unread
 * divider and the jump to the newest message, and how screen readers hear a bubble. The screen is stateless: these
 * check its events and its list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()
    private var backPressed = false

    @Test
    fun `the back button leaves the screen`() {
        setScreen(ChatPreviewData.privateChat)

        composeRule.onNodeWithContentDescription(string(R.string.chat_back)).performClick()

        assertTrue(backPressed)
    }

    @Test
    fun `try again after an error sends Retry`() {
        setScreen(ChatPreviewData.error)

        composeRule.onNodeWithText(string(R.string.chat_retry)).performClick()

        assertEquals(listOf(ChatEvent.Retry), events)
    }

    @Test
    fun `a list that shows its oldest message asks for older ones`() {
        setScreen(ChatPreviewData.privateChat)

        assertEquals(ChatEvent.NearOldestChanged(true), events.last())
    }

    @Test
    fun `the list reports the messages it shows, every item of an album`() {
        setScreen(ChatPreviewData.mediaPhotos)

        val shown = events.filterIsInstance<ChatEvent.MessagesVisible>().last().messageIds
        assertEquals(setOf(48L, 49L, 50L, 51L, 52L, 53L), shown)
    }

    @Test
    fun `the list reports only the messages on screen`() {
        setScreen(longChat(unread = 0))

        val shown = events.filterIsInstance<ChatEvent.MessagesVisible>().last().messageIds
        // "Message 1", the newest, is the last id; the oldest is far above.
        assertTrue(MESSAGES.toLong() in shown)
        assertFalse(1L in shown)
    }

    @Test
    fun `a long list opens at the unread divider, away from the newest message`() {
        setScreen(longChat(unread = UNREAD))

        val divider = composeRule.onNodeWithText(string(R.string.chat_unread_divider)).assertIsDisplayed()
        val screenHeight =
            composeRule
                .onRoot()
                .fetchSemanticsNode()
                .size.height
        assertTrue("near the top", divider.fetchSemanticsNode().boundsInRoot.top < screenHeight / 2)
        composeRule
            .onNodeWithContentDescription(
                string(R.string.chat_jump_to_bottom),
                substring = true,
            ).assertIsDisplayed()
    }

    @Test
    fun `a long list without unread messages opens at the newest message`() {
        setScreen(longChat(unread = 0))

        composeRule.onNodeWithContentDescription(NEWEST).assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(
                string(R.string.chat_jump_to_bottom),
                substring = true,
            ).assertDoesNotExist()
    }

    @Test
    fun `the jump button scrolls to the newest message`() {
        setScreen(longChat(unread = UNREAD))

        composeRule.onNodeWithContentDescription(string(R.string.chat_jump_to_bottom), substring = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(NEWEST).assertIsDisplayed()
    }

    @Test
    fun `a new message comes into view at the bottom`() {
        var state by mutableStateOf(longChat(unread = 0))
        setScreen { state }

        state = longChat(unread = 0, extra = 1)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Ada, 9:41 AM: Message 0").assertIsDisplayed()
    }

    @Test
    fun `the first message of a new day comes into view with its date`() {
        var state by mutableStateOf(longChat(unread = 0))
        setScreen { state }

        state = longChat(unread = 0, extra = 1, newDay = true)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Ada, 9:41 AM: Message 0").assertIsDisplayed()
    }

    @Test
    fun `a new message leaves the list where it is when scrolled back in the history`() {
        // It opens at the unread divider, away from the newest message.
        var state by mutableStateOf(longChat(unread = UNREAD))
        setScreen { state }

        state = longChat(unread = UNREAD, extra = 1)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.chat_unread_divider)).assertIsDisplayed()
    }

    @Test
    fun `screen readers hear a bubble as one, with its sender, time, content and state`() {
        setScreen(ChatPreviewData.privateChat)

        composeRule.onNodeWithContentDescription("You, 9:41 AM: Sending the slides now, sending").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ada, 9:46 AM: See you there!").assertIsDisplayed()
    }

    @Test
    fun `screen readers play a voice message, and pause it while it plays`() {
        var state by mutableStateOf(ChatPreviewData.mediaFiles)
        setScreen { state }

        performCustomAction(string(R.string.content_play))
        state = ChatPreviewData.voicePlaying
        composeRule.waitForIdle()

        assertEquals(
            listOf(ChatEvent.PlayPause(VOICE_MESSAGE, VOICE_FILE)),
            events.filterIsInstance<ChatEvent.PlayPause>(),
        )
        composeRule.onNode(hasCustomAction(string(R.string.content_pause))).assertExists()
        composeRule.onNode(hasCustomAction(string(R.string.content_play))).assertDoesNotExist()
    }

    @Test
    fun `screen readers hear spoilers as such until they are revealed`() {
        setScreen(ChatPreviewData.formattedText)

        // The incoming and the outgoing sample.
        composeRule.onAllNodesWithContentDescription("hide spoiler, and hold", substring = true).assertCountEquals(2)
        composeRule.onAllNodesWithContentDescription("hide a spoiler", substring = true).assertCountEquals(0)
    }

    private fun setScreen(state: ChatUiState) = setScreen { state }

    private fun setScreen(state: () -> ChatUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(state = state(), onEvent = { events += it }, onBack = { backPressed = true })
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * [MESSAGES] incoming messages, "Message 1" the newest, with the unread divider above the [unread] newest; [extra]
     * newer ones, "Message 0" and down, arrived after them, on a [newDay] with its date separator if so.
     */
    private fun longChat(
        unread: Int,
        extra: Int = 0,
        newDay: Boolean = false,
    ): ChatUiState {
        val today = UiText.resource(R.string.chat_date_today)
        val items =
            ((1 - extra)..MESSAGES).flatMap { index ->
                val text = FormattedText("Message $index", persistentListOf())
                val bubble =
                    ChatItem.Bubble(
                        id = (MESSAGES - index + 1).toLong(),
                        isOutgoing = false,
                        position = GroupPosition.Single,
                        sender = UiText.Plain("Ada"),
                        senderId = 2,
                        showsSenderName = false,
                        avatar = null,
                        hasAvatarColumn = false,
                        time = "9:41 AM",
                        isEdited = false,
                        status = null,
                        forwardedFrom = null,
                        reply = null,
                        content = MessageContent.Text(text, linkPreview = null),
                        contentLabel = null,
                        text = text,
                        spoilersRevealed = false,
                        linkPreview = null,
                        reactions = persistentListOf(),
                        date = today,
                    )
                when {
                    unread > 0 && index == unread -> listOf(bubble, ChatItem.UnreadDivider(today))
                    newDay && index == 1 - extra -> listOf(bubble, ChatItem.DateSeparator(epochDay = 2, date = today))
                    else -> listOf(bubble)
                }
            } + ChatItem.DateSeparator(epochDay = 1, date = today)
        return ChatUiState(
            header = ChatPreviewData.privateChat.header,
            content =
                ChatContent.Content(
                    items.toImmutableList(),
                    reachedStart = true,
                    isLoadingOlder = false,
                    olderError = null,
                ),
            unreadCount = unread,
        )
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    /** Does what the custom accessibility action [label] of the one node that offers it does, as TalkBack would. */
    private fun performCustomAction(label: String) {
        val node = composeRule.onNode(hasCustomAction(label)).fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].single { it.label == label }
        composeRule.runOnIdle { action.action() }
    }

    /** A node offering the custom accessibility action [label]. */
    private fun hasCustomAction(label: String) =
        SemanticsMatcher("has the custom action $label") { node ->
            node.config
                .getOrNull(SemanticsActions.CustomActions)
                .orEmpty()
                .any { it.label == label }
        }

    private companion object {
        const val MESSAGES = 60
        const val UNREAD = 30

        /** The newest message of [longChat], as screen readers hear it. */
        const val NEWEST = "Ada, 9:41 AM: Message 1"

        /** The voice message of [ChatPreviewData.mediaFiles] and its file. */
        const val VOICE_MESSAGE = 63L
        val VOICE_FILE =
            ContentPreviewData
                .voiceNote(isListened = true)
                .voiceNote.file.id
    }
}
