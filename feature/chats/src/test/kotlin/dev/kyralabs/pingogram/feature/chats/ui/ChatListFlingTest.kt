package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import dev.kyralabs.pingogram.core.ui.component.scrollingListState
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A touch that stops the chat list's fling only stops it, as in Telegram's apps: held, it starts no selection, and
 * tapped, it opens no chat; the list stays where the touch stopped it. On the list at rest the same hold or tap, at the
 * same place, does what it always does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatListFlingTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatListEvent>()
    private val listState: LazyListState = scrollingListState()

    @Test
    fun `holding a row while the list flings stops the list and selects nothing`() {
        setList()
        fling()

        val at = aRow()
        hold(at)
        val stoppedAt = position()
        repeat(FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        assertEquals(stoppedAt, position())
        // The same hold on the list at rest, where the first one landed, selects the chat there.
        hold(at)

        assertEquals(1, events.count { it is ChatListEvent.ChatLongClicked })
    }

    @Test
    fun `a tap that stops the fling opens no chat`() {
        setList()
        fling()

        val at = aRow()
        composeRule.onRoot().performTouchInput { click(at) }
        repeat(FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        // The same tap on the list at rest opens the chat there.
        composeRule.onRoot().performTouchInput { click(at) }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)

        assertEquals(1, events.count { it is ChatListEvent.ChatClicked })
        assertEquals(0, events.count { it is ChatListEvent.ChatLongClicked })
    }

    private fun setList() {
        val row = ChatListPreviewData.items.first { it.topics == null && !it.isPinned }
        val rows = (1..ROWS).map { row.copy(id = FIRST_ID + it) }.toImmutableList()
        val state = ChatListPreviewData.content.copy(content = ChatListContent.Content(rows))
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatListScreen(state = state, onEvent = { events += it }, listState = listState)
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
    }

    /** Flings the list toward its end and lets a few frames of the fling run. */
    private fun fling() {
        val list = listBounds()
        composeRule.onRoot().performTouchInput {
            swipeUp(startY = list.bottom - list.height / 4f, endY = list.top, durationMillis = FLING_MILLIS)
        }
        repeat(FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        assertTrue("the list should still fling", listState.isScrollInProgress)
    }

    /** The middle of a row the list shows in the middle of its height, in the root's coordinates. */
    private fun aRow(): Offset {
        val list = listBounds()
        val rows =
            composeRule
                .onAllNodes(hasClickAction())
                .fetchSemanticsNodes()
                .map { it.boundsInRoot }
                // The rows: as wide as the list nearly, and wholly in it.
                .filter { it.width > list.width * ROW_WIDTH && it.top > list.top && it.bottom < list.bottom }
                .sortedBy { it.top }
        return rows[rows.size / 2].center
    }

    private fun listBounds() =
        composeRule.onNodeWithTag(CHAT_LIST, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** A finger down [at] a place, held past the long-press timeout, then lifted. */
    private fun hold(at: Offset) {
        composeRule.onRoot().performTouchInput { down(at) }
        composeRule.mainClock.advanceTimeBy(HOLD_MILLIS)
        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun position() = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset

    private companion object {
        const val ROWS = 200
        const val FIRST_ID = 1_000L
        const val CHAT_LIST = "chat_list"
        const val FLING_MILLIS = 60L
        const val FRAMES = 3
        const val ROW_WIDTH = 0.8f

        /** Past the long-press timeout (400 ms). */
        const val HOLD_MILLIS = 1_000L

        /** Past the double-tap timeout, so two taps are two gestures. */
        const val SETTLE_MILLIS = 1_000L
    }
}
