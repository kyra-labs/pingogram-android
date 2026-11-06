package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.folder
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The pinned chats' group ([ChatListContent.Content.pinnedCount]): the leading chats the list pins. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListPinnedTest : ChatListViewModelHarness() {
    @Test
    fun `counts the pinned chats TDLib puts first`() =
        runTest {
            val main = listOf(ChatListType.Main)
            chats.setChats(
                ChatListType.Main,
                chat(id = 2, pinnedIn = main),
                chat(id = 3, pinnedIn = main),
                chat(id = 4),
                chat(id = 5),
            )

            assertEquals(2, viewModel().pinnedCount())
        }

    @Test
    fun `no pinned chats, no group`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2), chat(id = 3))

            assertEquals(0, viewModel().pinnedCount())
        }

    @Test
    fun `every chat pinned makes one group of all`() =
        runTest {
            val main = listOf(ChatListType.Main)
            chats.setChats(ChatListType.Main, chat(id = 2, pinnedIn = main), chat(id = 3, pinnedIn = main))

            assertEquals(2, viewModel().pinnedCount())
        }

    @Test
    fun `a chat pinned or unpinned changes the group`() =
        runTest {
            val main = listOf(ChatListType.Main)
            chats.setChats(ChatListType.Main, chat(id = 2, pinnedIn = main), chat(id = 3))
            val viewModel = viewModel()

            chats.setChats(ChatListType.Main, chat(id = 3, pinnedIn = main), chat(id = 2, pinnedIn = main))
            runCurrent()

            assertEquals(2, viewModel.pinnedCount())
        }

    @Test
    fun `a folder's own pins make its group`() =
        runTest {
            val folder = ChatListType.Folder(1)
            chats.folderList.value = persistentListOf(folder(1))
            chats.setChats(folder, chat(id = 2, lists = listOf(folder), pinnedIn = listOf(folder)), chat(id = 3))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.SelectFolder(1))

            assertEquals(1, viewModel.pinnedCount())
        }

    private fun ChatListViewModel.pinnedCount() = (state.content as ChatListContent.Content).pinnedCount
}
