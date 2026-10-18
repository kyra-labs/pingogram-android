package dev.kyralabs.pingogram.feature.chats.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.folder
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Selection mode (FR-L8), the archive row (FR-L3) and the archive (FR-L9) of [ChatListViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListSelectionTest : ChatListViewModelHarness() {
    // Selection mode

    @Test
    fun `a long press starts selection mode with the chat`() =
        runTest {
            val viewModel = mainList()

            send(viewModel, ChatListEvent.ChatLongClicked(3))

            assertEquals(setOf(3L), viewModel.selectedIds())
        }

    @Test
    fun `a tap outside selection mode selects nothing`() =
        runTest {
            val viewModel = mainList()

            send(viewModel, ChatListEvent.ChatClicked(3))

            assertNull(viewModel.state.selection)
        }

    @Test
    fun `a tap in selection mode selects a chat, and deselects it again`() =
        runTest {
            val viewModel = mainList()
            send(viewModel, ChatListEvent.ChatLongClicked(2), ChatListEvent.ChatClicked(3))
            assertEquals(setOf(2L, 3L), viewModel.selectedIds())

            send(viewModel, ChatListEvent.ChatClicked(2))

            assertEquals(setOf(3L), viewModel.selectedIds())
        }

    @Test
    fun `a long press in selection mode toggles the chat`() =
        runTest {
            val viewModel = mainList()

            send(viewModel, ChatListEvent.ChatLongClicked(2), ChatListEvent.ChatLongClicked(3))

            assertEquals(setOf(2L, 3L), viewModel.selectedIds())
        }

    @Test
    fun `deselecting the last chat leaves selection mode`() =
        runTest {
            val viewModel = mainList()

            send(viewModel, ChatListEvent.ChatLongClicked(2), ChatListEvent.ChatClicked(2))

            assertNull(viewModel.state.selection)
        }

    @Test
    fun `clearing the selection leaves selection mode`() =
        runTest {
            val viewModel = mainList()

            send(
                viewModel,
                ChatListEvent.ChatLongClicked(2),
                ChatListEvent.ChatClicked(3),
                ChatListEvent.ClearSelection,
            )

            assertNull(viewModel.state.selection)
        }

    @Test
    fun `selecting another folder clears the selection`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1))
            chats.setChats(
                ChatListType.Folder(1),
                chat(id = 2, lists = listOf(ChatListType.Main, ChatListType.Folder(1))),
            )
            val viewModel = mainList()
            send(viewModel, ChatListEvent.ChatLongClicked(2))

            send(viewModel, ChatListEvent.SelectFolder(1))

            assertNull(viewModel.state.selection)
        }

    @Test
    fun `selecting the folder shown keeps the selection`() =
        runTest {
            val viewModel = mainList()
            send(viewModel, ChatListEvent.ChatLongClicked(2))

            send(viewModel, ChatListEvent.SelectFolder(null))

            assertEquals(setOf(2L), viewModel.selectedIds())
        }

    @Test
    fun `the selection survives process death`() =
        runTest {
            val viewModel = mainList()
            send(viewModel, ChatListEvent.ChatLongClicked(2), ChatListEvent.ChatClicked(4))

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

            assertEquals(setOf(2L, 4L), restored.selectedIds())
        }

    @Test
    fun `a selected chat that leaves the list is no longer selected`() =
        runTest {
            val viewModel = mainList()
            send(viewModel, ChatListEvent.ChatLongClicked(2), ChatListEvent.ChatClicked(3))

            chats.setChats(ChatListType.Main, chat(id = 3), chat(id = 4))
            runCurrent()

            assertEquals(setOf(3L), viewModel.selectedIds())
            assertEquals(
                listOf(ChatRowAction.Pin),
                viewModel.state.selection
                    ?.actions
                    ?.take(1),
            )
        }

    @Test
    fun `selection mode ends when every selected chat left the list`() =
        runTest {
            val viewModel = mainList()
            send(viewModel, ChatListEvent.ChatLongClicked(2))

            chats.setChats(ChatListType.Main, chat(id = 3))
            runCurrent()

            assertNull(viewModel.state.selection)
        }

    // The archive row

    @Test
    fun `the main list shows the archive row while the archive has chats`() =
        runTest {
            chats.unreadCounts.value = mapOf(ChatListType.Archive to UnreadChatCount(5, 2, 0, 0, 0))

            assertEquals(ArchiveEntry(5, persistentListOf(), unreadCount = 2), mainList().state.archive)
        }

    @Test
    fun `the archive row names the first archived chats loaded`() =
        runTest {
            chats.unreadCounts.value = mapOf(ChatListType.Archive to UnreadChatCount(9, 0, 0, 0, 0))
            val archived =
                listOf("Rocket Club", "", "Old Team", "Newsletter", "Fourth").mapIndexed { index, title ->
                    chat(id = 20L + index, title = title, lists = listOf(ChatListType.Archive))
                }
            chats.setChats(ChatListType.Archive, *archived.toTypedArray())

            assertEquals(listOf("Rocket Club", "Old Team", "Newsletter"), mainList().state.archive?.titles)
        }

    @Test
    fun `archived chats loaded before TDLib counted them show the row`() =
        runTest {
            chats.setChats(ChatListType.Archive, chat(id = 20, lists = listOf(ChatListType.Archive)))

            assertEquals(1, mainList().state.archive?.chatCount)
        }

    @Test
    fun `no archive row while the archive is empty`() =
        runTest {
            assertNull(mainList().state.archive)
        }

    @Test
    fun `no archive row in folders`() =
        runTest {
            chats.unreadCounts.value = mapOf(ChatListType.Archive to UnreadChatCount(5, 2, 0, 0, 0))
            chats.folderList.value = persistentListOf(folder(1))
            val viewModel = mainList()

            send(viewModel, ChatListEvent.SelectFolder(1))

            assertNull(viewModel.state.archive)
        }

    @Test
    fun `the archive row opens the archive`() =
        runTest {
            val viewModel = mainList()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatListEvent.OpenArchive)

            assertEquals(listOf(ChatListEffect.OpenArchive), effects)
        }

    // The archive

    @Test
    fun `the archive shows the archived chats and loads them, without folders or an archive row`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1))
            chats.unreadCounts.value = mapOf(ChatListType.Archive to UnreadChatCount(2, 0, 0, 0, 0))
            chats.setChats(ChatListType.Main, chat(id = 2))
            chats.setChats(
                ChatListType.Archive,
                chat(id = 5, lists = listOf(ChatListType.Archive)),
                chat(id = 6, lists = listOf(ChatListType.Archive)),
            )

            val viewModel = viewModel(kind = ChatListKind.Archive)

            assertEquals(ChatListKind.Archive, viewModel.state.kind)
            assertEquals(listOf(5L, 6L), viewModel.ids())
            assertEquals(listOf(ChatListType.Archive to ChatListViewModel.FIRST_PAGE_SIZE), chats.loadRequests)
            assertTrue(viewModel.state.folderTabs.isEmpty())
            assertNull(viewModel.state.archive)
        }

    @Test
    fun `the archive is the archive before its first emission`() =
        runTest {
            assertEquals(ChatListKind.Archive, createViewModel(kind = ChatListKind.Archive).uiState.value.kind)
        }

    @Test
    fun `an empty archive is empty`() =
        runTest {
            assertEquals(ChatListContent.Empty(isFolder = false), viewModel(kind = ChatListKind.Archive).state.content)
        }

    @Test
    fun `archived chats are unarchived by swiping`() =
        runTest {
            chats.setChats(ChatListType.Archive, chat(id = 5, lists = listOf(ChatListType.Archive)))

            assertEquals(ChatRowAction.Unarchive, viewModel(kind = ChatListKind.Archive).item(5).endSwipeAction)
        }

    @Test
    fun `the archive keeps its own selection`() =
        runTest {
            chats.setChats(ChatListType.Archive, chat(id = 5, lists = listOf(ChatListType.Archive)))
            val viewModel = viewModel(kind = ChatListKind.Archive)

            send(viewModel, ChatListEvent.ChatLongClicked(5))

            assertEquals(setOf(5L), viewModel.selectedIds())
        }

    /** A view model on the main list of chats 2, 3 and 4. */
    private fun TestScope.mainList(): ChatListViewModel {
        chats.setChats(ChatListType.Main, chat(id = 2), chat(id = 3), chat(id = 4))
        return viewModel()
    }

    private fun ChatListViewModel.selectedIds(): Set<Long>? = state.selection?.chatIds
}
