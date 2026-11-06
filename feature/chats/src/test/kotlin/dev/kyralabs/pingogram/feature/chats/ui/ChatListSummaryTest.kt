package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.ME
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.user
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The Chats tab header's summary ([ChatListViewModel.summary]): the status line's counts and the account's avatar. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListSummaryTest : ChatListViewModelHarness() {
    @Test
    fun `counts the main list's unread unmuted chats as TDLib does, and the chats that mention the user`() =
        runTest {
            chats.setChats(
                ChatListType.Main,
                chat(id = 2, unreadCount = 4, unreadMentionCount = 1),
                chat(id = 3, unreadCount = 1, unreadMentionCount = 2),
                chat(id = 4, unreadCount = 1),
            )
            chats.unreadCounts.value = mapOf(ChatListType.Main to unread(unreadUnmuted = 12))

            val summary = summaryOf(createViewModel())

            assertEquals(12, summary().unreadChats)
            assertEquals(2, summary().mentionChats)
            assertTrue(summary().hasStatus)
        }

    @Test
    fun `follows the counts as they change`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2, unreadCount = 1))
            chats.unreadCounts.value = mapOf(ChatListType.Main to unread(unreadUnmuted = 1))
            val summary = summaryOf(createViewModel())

            chats.setChats(ChatListType.Main, chat(id = 2), chat(id = 3, unreadMentionCount = 1))
            chats.unreadCounts.value = mapOf(ChatListType.Main to unread(unreadUnmuted = 0))
            runCurrent()

            assertEquals(0, summary().unreadChats)
            assertEquals(1, summary().mentionChats)
        }

    @Test
    fun `nothing unread hides the status line`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))

            assertFalse(summaryOf(createViewModel())().hasStatus)
        }

    @Test
    fun `locked chats never count as mentions`() =
        runTest {
            chats.setChats(
                ChatListType.Main,
                chat(id = 2, unreadMentionCount = 1),
                chat(id = 3, unreadMentionCount = 1),
            )
            chatLock.lock(setOf(3L))

            assertEquals(1, summaryOf(createViewModel())().mentionChats)
        }

    @Test
    fun `counts the main list whatever folder is shown`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2, unreadMentionCount = 1))
            chats.unreadCounts.value =
                mapOf(
                    ChatListType.Main to unread(unreadUnmuted = 5),
                    ChatListType.Folder(1) to unread(unreadUnmuted = 1),
                )
            val viewModel = createViewModel()
            val summary = summaryOf(viewModel)

            send(viewModel, ChatListEvent.SelectFolder(1))

            assertEquals(5, summary().unreadChats)
            assertEquals(1, summary().mentionChats)
        }

    @Test
    fun `carries the signed-in user for the header's avatar`() =
        runTest {
            val me = user(ME, firstName = "Ada", lastName = "Lovelace")
            users.setUsers(me)

            val account = summaryOf(createViewModel())().account

            assertEquals(HeaderAccount(ME, me.fullName(), photo = null), account)
        }

    @Test
    fun `has no account until TDLib named the user`() =
        runTest {
            assertNull(summaryOf(createViewModel())().account)
        }

    @Test
    fun `the archive has no summary`() =
        runTest {
            chats.unreadCounts.value = mapOf(ChatListType.Main to unread(unreadUnmuted = 3))

            assertEquals(ChatListSummary(), summaryOf(createViewModel(kind = ChatListKind.Archive))())
        }

    /** Collects [viewModel]'s summary as the header does, and reads its latest value. */
    private fun TestScope.summaryOf(viewModel: ChatListViewModel): () -> ChatListSummary {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.summary.collect {} }
        runCurrent()
        return { viewModel.summary.value }
    }

    private fun unread(unreadUnmuted: Int) =
        UnreadChatCount(
            totalCount = 20,
            unreadCount = unreadUnmuted,
            unreadUnmutedCount = unreadUnmuted,
            markedAsUnreadCount = 0,
            markedAsUnreadUnmutedCount = 0,
        )
}
