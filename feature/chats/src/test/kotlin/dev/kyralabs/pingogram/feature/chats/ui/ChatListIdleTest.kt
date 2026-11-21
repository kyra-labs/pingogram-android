package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chat list at a start (`docs/PERFORMANCE.md` §4, "An idle screen draws no frames"): TDLib connects, then catches
 * up, changing a chat below the fold, the header's counts and the folders' counts all the while, then is ready. Once
 * nothing visible changes, no frame is requested, with the header expanded and, as at a warm start that restores the
 * list scrolled, collapsed; and a count that changes lays out its own text once, not on every frame
 * ([TextLayoutCounter]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT, shadows = [TextLayoutCounter::class])
class ChatListIdleTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var connection by mutableStateOf(ConnectionStatus.Connecting)

    /** Work's unread count, which TDLib catching up changes. */
    private var workUnread by mutableIntStateOf(2)

    /** How many messages a chat far below the fold has unread, which TDLib catching up changes. */
    private var unreadBelow by mutableIntStateOf(0)

    /** The header's status line and Catch-up chip: "16 unread · Catch up · 15 chats" once caught up. */
    private var summary by mutableStateOf(ChatListSummary(unreadChats = 3, catchUpChats = 2))

    private val items =
        (0 until ROWS)
            .map { index ->
                val base = ChatListPreviewData.items[index % ChatListPreviewData.items.size]
                base.copy(id = FIRST_ID + index, isPinned = false)
            }.toImmutableList()

    @Before
    @After
    fun forgetTextLayouts() = TextLayoutCounter.reset()

    @Test
    fun `once connected and caught up, the chat list draws no frames`() {
        setScreen()

        catchUp()

        assertIdle("connected")
    }

    @Test
    fun `with the header collapsed, the hidden connection pill draws no frames`() {
        setScreen()
        composeRule.onNodeWithTag(CHAT_LIST_TAG).performTouchInput {
            swipeUp(startY = bottom * SWIPE_FROM, endY = top, durationMillis = SWIPE_MILLIS)
        }
        repeat(SETTLE_FRAMES) { frame() }

        // The collapsed bar shows "Connecting…" as still text; the expanded row's pill is out of sight.
        assertIdle("connecting")
        connection = ConnectionStatus.Updating
        assertIdle("updating")
        catchUp()
        assertIdle("connected")
    }

    @Test
    fun `connected with the header expanded, counts changing on every frame stop drawing once they settle`() {
        connection = ConnectionStatus.Ready
        setScreen()

        repeat(CHANGE_FRAMES) {
            countsChange(it)
            frame()
        }
        summary = CAUGHT_UP

        assertIdle("counts settled")
    }

    @Test
    fun `a changing count lays out its own text once`() {
        connection = ConnectionStatus.Ready
        summary = CAUGHT_UP
        setScreen()

        assertLaysOutOnce("unread", "17 unread") { summary = summary.copy(unreadChats = 17) }
        assertLaysOutOnce("Catch-up", "Catch up · 16 chats") { summary = summary.copy(catchUpChats = 16) }
        assertLaysOutOnce("Work's count", "3") { workUnread = 3 }
    }

    /** Connecting, then updating while the chat below the fold and the counts change, then connected. */
    private fun catchUp() {
        connection = ConnectionStatus.Connecting
        repeat(CHANGE_FRAMES) { frame() }
        connection = ConnectionStatus.Updating
        repeat(CHANGE_FRAMES) {
            countsChange(it)
            frame()
        }
        connection = ConnectionStatus.Ready
        repeat(CHANGE_FRAMES) {
            countsChange(it)
            frame()
        }
        summary = CAUGHT_UP
    }

    /** What catching up changes on its [frame]th frame: the chat below the fold, the header's and Work's counts. */
    private fun countsChange(frame: Int) {
        unreadBelow++
        workUnread = 2 + frame % COUNT_STEPS
        summary = ChatListSummary(unreadChats = 3 + frame % COUNT_STEPS * 4, catchUpChats = 2 + frame % COUNT_STEPS * 5)
    }

    /**
     * After [change], [text] is laid out once (with at most the text beside it in its row, which gives it room) and
     * no frame is requested by the time the motion scheme's slowest spring would have ended.
     */
    private fun assertLaysOutOnce(
        label: String,
        text: String,
        change: () -> Unit,
    ) {
        TextLayoutCounter.reset()
        change()
        repeat(SETTLE_FRAMES) { frame() }
        assertEquals("$label: layouts of \"$text\"", 1, TextLayoutCounter.texts[text])
        assertTrue("$label: texts laid out ${TextLayoutCounter.texts}", TextLayoutCounter.texts.values.sum() <= 2)
        assertFalse("$label: a frame is still requested", framePending())
    }

    /** Lets what [label] started settle, then asserts no frame is requested for [IDLE_FRAMES] frames. */
    private fun assertIdle(label: String) {
        repeat(SETTLE_FRAMES) { frame() }
        repeat(IDLE_FRAMES) {
            frame()
            assertFalse("$label: a frame is requested ${it * FRAME_MILLIS} ms after it settled", framePending())
        }
    }

    private fun framePending() = Recomposer.runningRecomposers.value.any { it.hasPendingWork }

    private fun setScreen() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatListScreen(
                    state =
                        ChatListUiState(
                            content = content(unreadBelow),
                            connection = connection,
                            folderTabs =
                                persistentListOf(
                                    FolderTab.AllChats(unreadCount = 4),
                                    FolderTab.Folder(
                                        id = 1,
                                        title = "Work",
                                        unreadCount = workUnread,
                                        iconName = "Work",
                                    ),
                                    FolderTab.Folder(id = 2, title = "Family", unreadCount = 0, iconName = "Favorite"),
                                ),
                        ),
                    onEvent = {},
                    summary = { summary },
                )
            }
        }
        repeat(SETTLE_FRAMES) { frame() }
    }

    private fun content(unread: Int): ChatListContent.Content {
        val changed = items.toMutableList()
        changed[BELOW] = changed[BELOW].copy(unreadCount = unread)
        return ChatListContent.Content(changed.toImmutableList(), pinnedCount = 0)
    }

    private fun frame() {
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private companion object {
        const val ROWS = 200
        const val FIRST_ID = 1_000L

        /** A chat well below the fold. */
        const val BELOW = 150
        const val CHAT_LIST_TAG = "chat_list"
        const val SWIPE_FROM = 0.8f
        const val SWIPE_MILLIS = 300L
        const val FRAME_MILLIS = 16

        /** Enough for the motion scheme's slowest spring and the pill's fade to end. */
        const val SETTLE_FRAMES = 90
        const val CHANGE_FRAMES = 60
        const val IDLE_FRAMES = 60

        /** How many values each count takes while catching up. */
        const val COUNT_STEPS = 4
        val CAUGHT_UP = ChatListSummary(unreadChats = 16, catchUpChats = 15)
    }
}
