package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.folder
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The folders beside the selected one ([ChatListViewModel.neighbourLists]), which a swipe of the list pulls in under
 * the finger: their first page is loaded ahead, only for the tabs beside the selected one, and a list that is no
 * longer beside it is dropped. Tabs: All chats, Work (1), Family (2), Channels (3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListNeighboursTest : ChatListViewModelHarness() {
    @BeforeEach
    fun folders() {
        chats.folderList.value = persistentListOf(folder(WORK), folder(FAMILY), folder(CHANNELS))
        chats.setChats(ChatListType.Main, chat(id = 2))
        chats.setChats(ChatListType.Folder(WORK), chat(id = 3, lists = listOf(ChatListType.Folder(WORK))))
        chats.setChats(ChatListType.Folder(FAMILY), chat(id = 4, lists = listOf(ChatListType.Folder(FAMILY))))
        chats.setChats(ChatListType.Folder(CHANNELS), chat(id = 5, lists = listOf(ChatListType.Folder(CHANNELS))))
    }

    @Test
    fun `the first page of the folder beside the one shown loads ahead, and no other`() =
        runTest {
            viewModel()

            assertEquals(listOf(ChatListType.Main, ChatListType.Folder(WORK)), loadedLists())
        }

    @Test
    fun `selecting a folder loads the first page of the folders now beside it, each once`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.SelectFolder(FAMILY))

            val requested = chats.loadRequests.map { it.first }
            assertEquals(
                setOf(ChatListType.Main, ChatListType.Folder(WORK), ChatListType.Folder(FAMILY))
                    .plus(ChatListType.Folder(CHANNELS)),
                requested.toSet(),
            )
            assertEquals(requested.distinct(), requested)
        }

    @Test
    fun `the neighbours' lists show their chats as the list would`() =
        runTest {
            val viewModel = viewModel()
            send(viewModel, ChatListEvent.SelectFolder(WORK))

            val neighbours = neighboursOf(viewModel)

            assertEquals(null, neighbours.previous?.folderId)
            assertEquals(listOf(2L), neighbours.previous?.ids())
            assertEquals(FAMILY, neighbours.next?.folderId)
            assertEquals(listOf(4L), neighbours.next?.ids())
        }

    @Test
    fun `All chats beside a folder shows its chats before the communities are read`() =
        runTest {
            communities.add(COMMUNITY, "Kyra Labs", chatIds = listOf(2))
            communities.joinedAnswered.value = false
            val viewModel = viewModel()
            send(viewModel, ChatListEvent.SelectFolder(WORK))
            val neighbours = collected(viewModel)

            assertEquals(listOf(2L), neighbours.last().previous?.ids())

            communities.joinedAnswered.value = true
            runCurrent()

            val main =
                neighbours
                    .last()
                    .previous
                    ?.state
                    ?.content as ChatListContent.Content
            assertTrue(main.items.isEmpty())
            assertEquals(
                listOf(2L),
                main.communities
                    .single()
                    .chats
                    .map { it.id },
            )
        }

    @Test
    fun `a folder no longer beside the selected one is dropped`() =
        runTest {
            val viewModel = viewModel()
            send(viewModel, ChatListEvent.SelectFolder(WORK))
            val neighbours = collected(viewModel)

            send(viewModel, ChatListEvent.SelectFolder(FAMILY))

            assertEquals(WORK, neighbours.last().previous?.folderId)
            assertEquals(CHANNELS, neighbours.last().next?.folderId)
            // All chats, beside Work only, is no longer followed: its chats changing changes nothing.
            val count = neighbours.size
            chats.setChats(ChatListType.Main, chat(id = 9))
            runCurrent()
            assertEquals(count, neighbours.size)
        }

    @Test
    fun `the first tab has nothing before it`() =
        runTest {
            val neighbours = neighboursOf(viewModel())

            assertNull(neighbours.previous)
            assertEquals(WORK, neighbours.next?.folderId)
        }

    @Test
    fun `the archive has no neighbours and loads nothing ahead`() =
        runTest {
            val viewModel = viewModel(kind = ChatListKind.Archive)

            assertEquals(FolderNeighbourLists(), neighboursOf(viewModel))
            assertEquals(listOf(ChatListType.Archive), loadedLists())
        }

    /** The lists whose pages were asked for, in order, each once. */
    private fun loadedLists() = chats.loadRequests.map { it.first }.distinct()

    private fun TestScope.neighboursOf(viewModel: ChatListViewModel): FolderNeighbourLists = collected(viewModel).last()

    /** Every value of [ChatListViewModel.neighbourLists] from now on, collected as the screen does. */
    private fun TestScope.collected(viewModel: ChatListViewModel): List<FolderNeighbourLists> {
        val values = mutableListOf<FolderNeighbourLists>()
        backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler),
        ) { viewModel.neighbourLists.collect(values::add) }
        runCurrent()
        return values
    }

    private fun FolderNeighbourList.ids() = (state.content as ChatListContent.Content).items.map { it.id }

    private companion object {
        const val WORK = 1
        const val FAMILY = 2
        const val CHANNELS = 3
        const val COMMUNITY = 40L
    }
}
