package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.folder
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.ceil

/**
 * The chat list's counts at their pace ([COUNT_PACE], owner decision 2026-11-21): while TDLib catching up changes them
 * many times a second, the header's counts and the tabs' badges change at most once a period and end on the latest;
 * the rows follow every change, and a tab added shows at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListCountPaceTest : ChatListViewModelHarness() {
    @Test
    fun `a burst of 100 count changes changes the header's counts at most once a period and ends on the last`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2, unreadCount = 1))
            val viewModel = createViewModel()
            val shown = mutableListOf<ChatListSummary>()
            backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler),
            ) { viewModel.summary.collect { shown += it } }
            afterCountPace()
            shown.clear()

            repeat(CHANGES) {
                chats.unreadCounts.value = mapOf(ChatListType.Main to unread(it + 1))
                chats.setChats(ChatListType.Main, chat(id = 2, unreadCount = 1, unreadMentionCount = it % 2))
                advanceTimeBy(STEP_MILLIS)
            }
            afterCountPace()

            assertAtPace(shown.size)
            assertEquals(CHANGES, shown.last().unreadChats)
            assertEquals((CHANGES - 1) % 2, shown.last().mentionChats)
        }

    @Test
    fun `a burst of 100 count changes changes the tabs' badges at most once a period, the rows every time`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1, "Work"))
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()
            val tabs = mutableListOf<List<FolderTab>>()
            val rows = mutableListOf<Int>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect { state ->
                    if (tabs.lastOrNull() != state.folderTabs) tabs += state.folderTabs
                    rows += state.rows().singleOrNull()?.unreadCount ?: -1
                }
            }
            afterCountPace()
            tabs.clear()
            rows.clear()

            repeat(CHANGES) {
                chats.unreadCounts.value = mapOf(ChatListType.Folder(1) to unread(it + 1))
                chats.setChats(ChatListType.Main, chat(id = 2, unreadCount = it + 1))
                advanceTimeBy(STEP_MILLIS)
            }
            afterCountPace()

            assertAtPace(tabs.size)
            assertEquals(
                CHANGES,
                tabs
                    .last()
                    .filterIsInstance<FolderTab.Folder>()
                    .single()
                    .unreadCount,
            )
            // Every change reached the rows: each step's state was collected.
            assertEquals((1..CHANGES).toList(), rows.distinct().filter { it > 0 })
        }

    @Test
    fun `a folder added shows at once`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1, "Work"))
            chats.unreadCounts.value = mapOf(ChatListType.Folder(1) to unread(1))
            val viewModel = viewModel()
            chats.unreadCounts.value = mapOf(ChatListType.Folder(1) to unread(2))
            runCurrent()

            chats.folderList.value = persistentListOf(folder(1, "Work"), folder(2, "Family"))
            runCurrent()

            assertEquals(listOf(1, 2), viewModel.state.folderTabs.mapNotNull { it.folderId })
        }

    private fun assertAtPace(shown: Int) {
        val burstMillis = CHANGES * STEP_MILLIS
        val most = ceil(burstMillis.toDouble() / COUNT_PACE.inWholeMilliseconds).toInt() + 1
        assertTrue(shown in 1..most, "$shown values shown for at most $most")
    }

    private fun ChatListUiState.rows() = (content as? ChatListContent.Content)?.allRows.orEmpty()

    private fun unread(unreadUnmuted: Int) =
        UnreadChatCount(
            totalCount = 200,
            unreadCount = unreadUnmuted,
            unreadUnmutedCount = unreadUnmuted,
            markedAsUnreadCount = 0,
            markedAsUnreadUnmutedCount = 0,
        )

    private companion object {
        const val CHANGES = 100
        const val STEP_MILLIS = 10L
    }
}
