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
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chat list at a start (`docs/PERFORMANCE.md` §4, "An idle screen draws no frames"): TDLib connects, then catches
 * up, changing a chat below the fold and a folder's count all the while, then is ready. Once nothing visible changes,
 * no frame is requested, with the header expanded and, as at a warm start that restores the list scrolled, collapsed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
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

    private val items =
        (0 until ROWS)
            .map { index ->
                val base = ChatListPreviewData.items[index % ChatListPreviewData.items.size]
                base.copy(id = FIRST_ID + index, isPinned = false)
            }.toImmutableList()

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

    /** Connecting, then updating while the chat below the fold and Work's count change, then connected. */
    private fun catchUp() {
        connection = ConnectionStatus.Connecting
        repeat(CHANGE_FRAMES) { frame() }
        connection = ConnectionStatus.Updating
        repeat(CHANGE_FRAMES) {
            unreadBelow++
            workUnread = 2 + it % 3
            frame()
        }
        connection = ConnectionStatus.Ready
        repeat(CHANGE_FRAMES) {
            unreadBelow++
            frame()
        }
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
    }
}
