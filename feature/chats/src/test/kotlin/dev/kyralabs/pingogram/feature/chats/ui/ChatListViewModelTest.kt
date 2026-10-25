package dev.kyralabs.pingogram.feature.chats.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.folder
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Loading, errors, paging and folders of [ChatListViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest : ChatListViewModelHarness() {
    // Loading, content, empty

    @Test
    fun `shows progress until the first chats arrive`() =
        runTest {
            chats.onLoadChats = loadsChatsForever

            createViewModel().uiState.test {
                assertEquals(ChatListContent.Loading, awaitItem().content)

                chats.setChats(ChatListType.Main, chat(id = 2))

                assertEquals(listOf(2L), (awaitItem().content as ChatListContent.Content).items.map { it.id })
            }
        }

    @Test
    fun `shows the chats in the repository's order`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 3), chat(id = 2), chat(id = 4))

            assertEquals(listOf(3L, 2L, 4L), viewModel().ids())
        }

    @Test
    fun `locked chats never show in the list or the archive row, which points to them instead`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 3), chat(id = 2), chat(id = 4))
            chats.setChats(ChatListType.Archive, chat(id = 7, lists = listOf(ChatListType.Archive)))
            chatLock.lock(setOf(2L, 7L))
            val viewModel = viewModel()

            assertEquals(listOf(3L, 4L), viewModel.ids())
            assertNull(viewModel.state.archive)
            assertEquals(true, viewModel.state.hasLockedChats)

            // Open or not, they keep to their own list.
            chatLock.setUnlocked(true)
            runCurrent()
            assertEquals(listOf(3L, 4L), viewModel.ids())

            chatLock.lock(emptySet())
            runCurrent()
            assertEquals(listOf(3L, 2L, 4L), viewModel.ids())
            assertEquals(false, viewModel.state.hasLockedChats)
        }

    @Test
    fun `a main list without chats is empty`() =
        runTest {
            assertEquals(ChatListContent.Empty(isFolder = false), viewModel().state.content)
        }

    @Test
    fun `a folder without chats is empty as a folder`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.SelectFolder(1))

            assertEquals(ChatListContent.Empty(isFolder = true), viewModel.state.content)
        }

    @Test
    fun `the connection status is shown`() =
        runTest {
            val viewModel = viewModel()

            connection.connectionStatus.value = ConnectionStatus.WaitingForNetwork
            runCurrent()

            assertEquals(ConnectionStatus.WaitingForNetwork, viewModel.state.connection)
        }

    // Errors

    @Test
    fun `a network failure without chats shows the error`() =
        runTest {
            chats.onLoadChats = { _, _ -> Outcome.Failure(DomainError.Network) }

            assertEquals(ChatListContent.Error(DomainError.Network), viewModel().state.content)
        }

    @Test
    fun `a flood wait without chats shows how long to wait`() =
        runTest {
            chats.onLoadChats = { _, _ -> Outcome.Failure(DomainError.FloodWait(42)) }

            assertEquals(ChatListContent.Error(DomainError.FloodWait(42)), viewModel().state.content)
        }

    @Test
    fun `retry loads the first page again, showing progress`() =
        runTest {
            chats.onLoadChats = { _, _ -> Outcome.Failure(DomainError.Network) }
            val viewModel = viewModel()
            chats.onLoadChats = loadsChatsForever

            send(viewModel, ChatListEvent.Retry)

            assertEquals(ChatListContent.Loading, viewModel.state.content)
            assertEquals(listOf(ChatListType.Main to 20, ChatListType.Main to 20), chats.loadRequests)
        }

    @Test
    fun `a successful retry shows the chats`() =
        runTest {
            chats.onLoadChats = { _, _ -> Outcome.Failure(DomainError.Network) }
            val viewModel = viewModel()
            chats.onLoadChats = { _, _ -> Outcome.Success(true) }

            send(viewModel, ChatListEvent.Retry)
            chats.setChats(ChatListType.Main, chat(id = 2))
            runCurrent()

            assertEquals(listOf(2L), viewModel.ids())
        }

    @Test
    fun `a failed page keeps the chats shown`() =
        runTest {
            chats.onLoadChats = { _, _ -> Outcome.Success(true) }
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()
            chats.onLoadChats = { _, _ -> Outcome.Failure(DomainError.Network) }

            send(viewModel, nearEnd)

            assertEquals(listOf(2L), viewModel.ids())
        }

    // Paging

    @Test
    fun `loads a first page of 20 chats of the main list`() =
        runTest {
            viewModel()

            assertEquals(listOf(ChatListType.Main to 20), chats.loadRequests)
        }

    @Test
    fun `while the end is near, pages load one after another until the list is complete`() =
        runTest {
            // Pages that add no rows, as nothing reaches the fake's chats: the end stays near.
            chats.answerLoads(ok, ok, ok)
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()

            send(viewModel, nearEnd)

            val main = ChatListType.Main
            assertEquals(listOf(main to 20, main to PAGE, main to PAGE, main to PAGE), chats.loadRequests)
        }

    @Test
    fun `the end coming near during the first page loads the next page after it`() =
        runTest {
            val firstPage = CompletableDeferred<Outcome<Boolean>>()
            chats.onLoadChats = { _, limit -> if (limit == 20) firstPage.await() else Outcome.Success(false) }
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()
            send(viewModel, nearEnd)
            assertEquals(listOf(ChatListType.Main to 20), chats.loadRequests)

            firstPage.complete(ok)
            runCurrent()

            assertEquals(listOf(ChatListType.Main to 20, ChatListType.Main to PAGE), chats.loadRequests)
        }

    @Test
    fun `leaving the end stops loading after the page in progress`() =
        runTest {
            val nextPage = CompletableDeferred<Outcome<Boolean>>()
            chats.onLoadChats = { _, limit -> if (limit == 20) ok else nextPage.await() }
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()
            send(viewModel, nearEnd, awayFromEnd)

            nextPage.complete(ok)
            runCurrent()

            assertEquals(listOf(ChatListType.Main to 20, ChatListType.Main to PAGE), chats.loadRequests)
        }

    @Test
    fun `a failed page is not retried by itself`() =
        runTest {
            chats.answerLoads(ok, Outcome.Failure(DomainError.Network))
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()

            send(viewModel, nearEnd)
            advanceUntilIdle()

            assertEquals(listOf(ChatListType.Main to 20, ChatListType.Main to PAGE), chats.loadRequests)
        }

    @Test
    fun `the end coming near again retries a failed page`() =
        runTest {
            chats.answerLoads(ok, Outcome.Failure(DomainError.Network))
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()

            send(viewModel, nearEnd, awayFromEnd, nearEnd)

            assertEquals(
                listOf(ChatListType.Main to 20, ChatListType.Main to PAGE, ChatListType.Main to PAGE),
                chats.loadRequests,
            )
        }

    @Test
    fun `stops loading once the list is complete`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()

            send(viewModel, nearEnd, ChatListEvent.Retry)

            assertEquals(listOf(ChatListType.Main to 20), chats.loadRequests)
        }

    @Test
    fun `loads one page of a list at a time`() =
        runTest {
            chats.onLoadChats = loadsChatsForever
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()

            send(viewModel, nearEnd, awayFromEnd, nearEnd, ChatListEvent.Retry)

            assertEquals(listOf(ChatListType.Main to 20), chats.loadRequests)
        }

    @Test
    fun `another folder's pages wait for its own end to come near`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1))
            chats.answerLoads(ok)
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()
            send(viewModel, nearEnd)
            chats.answerLoads(ok)

            send(viewModel, ChatListEvent.SelectFolder(1))

            assertEquals(
                listOf(ChatListType.Main to 20, ChatListType.Main to PAGE, ChatListType.Folder(1) to 20),
                chats.loadRequests,
            )
        }

    // Folders

    @Test
    fun `selecting a folder shows its chats and loads its first page`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1))
            chats.setChats(ChatListType.Main, chat(id = 2))
            chats.setChats(ChatListType.Folder(1), chat(id = 3, lists = listOf(ChatListType.Folder(1))))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.SelectFolder(1))

            assertEquals(1, viewModel.state.selectedFolderId)
            assertEquals(listOf(3L), viewModel.ids())
            assertEquals(listOf(ChatListType.Main to 20, ChatListType.Folder(1) to 20), chats.loadRequests)
        }

    @Test
    fun `selecting All chats returns to the main list without loading it again`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1))
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.SelectFolder(1), ChatListEvent.SelectFolder(null))

            assertNull(viewModel.state.selectedFolderId)
            assertEquals(listOf(2L), viewModel.ids())
            assertEquals(listOf(ChatListType.Main to 20, ChatListType.Folder(1) to 20), chats.loadRequests)
        }

    @Test
    fun `the selected folder survives process death`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1))
            send(viewModel(), ChatListEvent.SelectFolder(1))

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

            assertEquals(1, restored.state.selectedFolderId)
        }

    @Test
    fun `a deleted folder falls back to the main list`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1), folder(2))
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()
            send(viewModel, ChatListEvent.SelectFolder(1))

            chats.folderList.value = persistentListOf(folder(2))
            runCurrent()

            assertNull(viewModel.state.selectedFolderId)
            assertEquals(listOf(2L), viewModel.ids())
        }

    @Test
    fun `without folders there are no tabs`() =
        runTest {
            assertEquals(persistentListOf<FolderTab>(), viewModel().state.folderTabs)
        }

    @Test
    fun `All chats sits among the folders where TDLib puts it, each tab with its unread unmuted chats`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1, "Work"), folder(2, "Family"))
            chats.mainPosition.value = 1
            chats.unreadCounts.value =
                mapOf(
                    ChatListType.Main to UnreadChatCount(10, 6, 4, 0, 0),
                    ChatListType.Folder(1) to UnreadChatCount(5, 3, 2, 0, 0),
                )

            assertEquals(
                listOf(
                    FolderTab.Folder(1, "Work", unreadCount = 2),
                    FolderTab.AllChats(unreadCount = 4),
                    FolderTab.Folder(2, "Family", unreadCount = 0),
                ),
                viewModel().state.folderTabs,
            )
        }

    @Test
    fun `All chats goes last when TDLib puts it beyond the folders`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1))
            chats.mainPosition.value = 5

            assertEquals(FolderTab.AllChats(0), viewModel().state.folderTabs.last())
        }
}
