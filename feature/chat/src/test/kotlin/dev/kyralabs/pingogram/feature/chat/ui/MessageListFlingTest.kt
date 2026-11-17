package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.component.scrollingListState
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A touch that stops the conversation's fling only stops it, as in Telegram's apps: held, it opens no message's
 * actions, and the list stays where the touch stopped it; in selection mode, tapped, it selects nothing. On the list at
 * rest the same hold or tap, at the same place, does what it always does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MessageListFlingTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<ChatEvent>()
    private val listState: LazyListState = scrollingListState()

    @Test
    fun `holding a message while the list flings stops the list and opens nothing`() {
        setConversation()
        composeRule.mainClock.autoAdvance = false
        fling()

        val at = aMessage()
        hold(at)
        val stoppedAt = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        repeat(FLING_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        assertEquals(stoppedAt, listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
        // The same hold on the list at rest, where the first one landed, opens the actions of the message there.
        hold(at)

        assertEquals(1, actions.size)
    }

    @Test
    fun `holding a message on a list at rest opens its actions`() {
        setConversation()
        composeRule.mainClock.autoAdvance = false

        hold(aMessage())

        assertEquals(1, actions.size)
    }

    @Test
    fun `in selection mode a tap that stops the fling selects nothing`() {
        setConversation(selecting = true)
        composeRule.mainClock.autoAdvance = false
        fling()

        val at = aMessage()
        list().performTouchInput { click(at) }
        repeat(FLING_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.mainClock.advanceTimeBy(HOLD_MILLIS)
        // The same tap on the list at rest selects the message there.
        list().performTouchInput { click(at) }
        composeRule.mainClock.advanceTimeByFrame()

        assertEquals(1, actions.count { it is SelectionEvent.Toggle })
    }

    /** Flings the list toward older messages and lets a few frames of the fling run. */
    private fun fling() {
        list().performTouchInput { swipeDown(startY = top + height / 4f, endY = bottom, durationMillis = FLING_MILLIS) }
        repeat(FLING_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        assertTrue("the list should still fling", listState.isScrollInProgress)
    }

    private fun setConversation(selecting: Boolean = false) {
        val state =
            ChatUiState(
                header = ChatPreviewData.privateChat.header,
                content =
                    ChatContent.Content(
                        (MESSAGES downTo 1).map(::bubble).toImmutableList(),
                        reachedStart = true,
                        isLoadingOlder = false,
                        null,
                    ),
                composer = ChatPreviewData.canWrite,
                selection =
                    if (selecting) MessageSelection(persistentSetOf(MESSAGES.toLong()), persistentListOf()) else null,
            )
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(
                    state = state,
                    onEvent = { if (it is ChatEvent.ShowMessageActions || it is SelectionEvent.Toggle) actions += it },
                    onBack = {},
                    listState = listState,
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** The middle of a message the list shows in the middle of its height, in the list's coordinates. */
    private fun aMessage(): Offset {
        val list = list().fetchSemanticsNode().boundsInRoot
        val bubbles =
            composeRule
                .onAllNodes(hasContentDescription(MESSAGE_TEXT, substring = true))
                .fetchSemanticsNodes()
                .map { it.boundsInRoot }
                .filter { it.top > list.top && it.bottom < list.bottom }
                .sortedBy { it.top }
        return bubbles[bubbles.size / 2].center - list.topLeft
    }

    /** A finger down [at] a place in the list, held past the long-press timeout, then lifted. */
    private fun hold(at: Offset) {
        list().performTouchInput { down(at) }
        composeRule.mainClock.advanceTimeBy(HOLD_MILLIS)
        list().performTouchInput { up() }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun list() = composeRule.onNodeWithTag(MESSAGE_LIST_TEST_TAG)

    private fun bubble(id: Int): ChatItem.Bubble {
        val formatted = FormattedText("Message $id, $MESSAGE_TEXT", persistentListOf())
        return ChatItem.Bubble(
            id = id.toLong(),
            isOutgoing = id % 2 == 0,
            position = GroupPosition.Single,
            sender = UiText.Plain("Ada"),
            senderId = 2,
            showsSenderName = false,
            avatar = null,
            hasAvatarColumn = false,
            time = "9:41 AM",
            isEdited = false,
            status = if (id % 2 == 0) OutgoingStatus.Sent else null,
            forwardedFrom = null,
            reply = null,
            content = MessageContent.Text(formatted, linkPreview = null),
            contentLabel = null,
            text = formatted,
            spoilersRevealed = false,
            linkPreview = null,
            reactions = persistentListOf(),
            date = UiText.Plain("Today"),
        )
    }

    private companion object {
        const val MESSAGES = 300
        const val MESSAGE_TEXT = "long enough to fill a bubble's line or two"
        const val FLING_MILLIS = 60L
        const val FLING_FRAMES = 3

        /** Past the long-press timeout (400 ms). */
        const val HOLD_MILLIS = 1_000L
    }
}
