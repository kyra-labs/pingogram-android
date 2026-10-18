package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One swipe runs its action once (FR-L4), whatever happens to the row while it springs back: the action changes the
 * row (in a folder an archived chat stays, its swipe turning to unarchive; a read chat's swipe turns to unread), or the
 * row leaves the list and comes back, as an archived chat does with Undo. The screen runs in a host that changes its
 * rows as TDLib's updates would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatRowSwipeTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<ChatListEvent.RowAction>()

    private var state by mutableStateOf(ChatListPreviewData.contentWithFolders.copy(selectedFolderId = 1))

    @Test
    fun `archiving a chat that stays in the folder archives it once`() {
        setScreen()

        row(ADA).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(listOf(ChatListEvent.RowAction(ADA_ID, ChatRowAction.Archive)), actions)
    }

    @Test
    fun `after archiving in a folder the row swipes again, to unarchive`() {
        setScreen()
        row(ADA).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        row(ADA).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(
            listOf(
                ChatListEvent.RowAction(ADA_ID, ChatRowAction.Archive),
                ChatListEvent.RowAction(ADA_ID, ChatRowAction.Unarchive),
            ),
            actions,
        )
    }

    @Test
    fun `marking a chat as read by swiping marks it once, though its swipe turns to unread`() {
        setScreen()

        row(ADA).performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals(listOf(ChatListEvent.RowAction(ADA_ID, ChatRowAction.MarkAsRead)), actions)
    }

    @Test
    fun `a row that leaves the list while it springs back comes back at rest`() {
        // Enough rows below Ada that scrolling to the last one takes Ada's row out of the list.
        state = state.copy(content = ChatListContent.Content(rowsWithManyMore()))
        val listState = LazyListState()
        lateinit var scope: CoroutineScope
        composeRule.setContent {
            scope = rememberCoroutineScope()
            // Like an archived chat, the row leaves the list as soon as its action runs, while it springs back.
            Screen(applyActions = false, listState = listState) { scope.launch { listState.scrollToItem(LAST_ROW) } }
        }

        val resting = row(ADA).fetchSemanticsNode().boundsInRoot.left
        row(ADA).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        // Like Undo, the chat comes back.
        composeRule.runOnIdle { scope.launch { listState.scrollToItem(0) } }
        composeRule.waitForIdle()

        assertEquals(listOf(ChatListEvent.RowAction(ADA_ID, ChatRowAction.Archive)), actions)
        assertEquals(resting, row(ADA).fetchSemanticsNode().boundsInRoot.left)
    }

    private fun setScreen() = composeRule.setContent { Screen(applyActions = true) }

    /**
     * The chat list; with [applyActions], each action changes its chat's row as TDLib's updates would. [onAction] runs
     * after each action.
     */
    @Composable
    private fun Screen(
        applyActions: Boolean,
        listState: LazyListState = rememberLazyListState(),
        onAction: () -> Unit = {},
    ) {
        PingogramTheme(dynamicColor = false) {
            ChatListScreen(
                state = state,
                onEvent = { event ->
                    if (event is ChatListEvent.RowAction) {
                        actions += event
                        if (applyActions) state = state.applying(event)
                        onAction()
                    }
                },
                listState = listState,
            )
        }
    }

    private fun row(name: String): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription("$name,", substring = true) and hasClickAction())

    /** The preview's rows, then copies of them with other ids. */
    private fun rowsWithManyMore() =
        (ChatListPreviewData.items + (1..EXTRA_ROWS).map { ChatListPreviewData.items.last().copy(id = 100L + it) })
            .toImmutableList()

    private companion object {
        const val ADA = "Ada Lovelace"
        const val ADA_ID = 2L
        const val EXTRA_ROWS = 40
        const val LAST_ROW = 50
    }
}

/** The state after TDLib applied [event]: in a folder that keeps archived chats, an archived chat stays. */
private fun ChatListUiState.applying(event: ChatListEvent.RowAction): ChatListUiState {
    val content = content as ChatListContent.Content
    val items =
        content.items.map { item ->
            if (item.id != event.chatId) return@map item
            val isArchived =
                when (event.action) {
                    ChatRowAction.Archive -> true
                    ChatRowAction.Unarchive -> false
                    else -> ChatRowAction.Unarchive in item.actions
                }
            val read =
                when (event.action) {
                    ChatRowAction.MarkAsRead -> item.copy(unreadCount = 0, isMarkedAsUnread = false)
                    ChatRowAction.MarkAsUnread -> item.copy(isMarkedAsUnread = true)
                    else -> item
                }
            read.copy(actions = rowActions(read.isUnread, isArchived, read.isPinned, read.isMuted, canMute = true))
        }
    return copy(content = ChatListContent.Content(items.toImmutableList()))
}
