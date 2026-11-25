package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.KeywordAlertSettings
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * What the Chats tab's states read in common is read once: the selected list, the folder tabs, the rows' snapshot,
 * the archive row, the overlays and the keyword alert matches each feed every state that reads them from one
 * collection, while the screen collects the list, the neighbouring folders and the header at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListSharedSourcesTest : ChatListViewModelHarness() {
    @BeforeEach
    fun folders() {
        chats.folderList.value = persistentListOf(folder(WORK), folder(FAMILY))
        chats.setChats(ChatListType.Main, chat(id = 2))
        chats.setChats(ChatListType.Folder(WORK), chat(id = 3, lists = listOf(ChatListType.Folder(WORK))))
        keywordAlerts.settings.value = KeywordAlertSettings(showInChatList = true)
    }

    /** The view model, with its list, its neighbouring folders and its header collected, as by the screen. */
    private fun TestScope.shown(): ChatListViewModel =
        viewModel().also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.neighbourLists.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.summary.collect {} }
            runCurrent()
        }

    @Test
    fun `the folders are read once for the selected list and once for the tabs`() =
        runTest {
            val viewModel = shown()
            send(viewModel, ChatListEvent.SelectFolder(WORK))

            assertEquals(2, chats.folderList.subscriptionCount.value)
            // Read by the tabs alone.
            assertEquals(1, chats.mainPosition.subscriptionCount.value)
        }

    @Test
    fun `the keyword alert matches are read once`() =
        runTest {
            shown()

            assertEquals(1, keywordAlerts.settings.subscriptionCount.value)
        }

    @Test
    fun `the rows' snapshot is read once for the list and both folders beside it`() =
        runTest {
            val viewModel = shown()
            send(viewModel, ChatListEvent.SelectFolder(WORK))

            assertEquals(1, clock.minutes.subscriptionCount.value)
        }

    @Test
    fun `shared, the list and its neighbours show what they showed`() =
        runTest {
            val viewModel = shown()
            send(viewModel, ChatListEvent.SelectFolder(WORK))

            assertEquals(listOf(3L), viewModel.ids())
            assertEquals(
                listOf(2L),
                viewModel.neighbourLists.value.previous
                    ?.ids(),
            )
            assertEquals(
                FAMILY,
                viewModel.neighbourLists.value.next
                    ?.folderId,
            )
        }

    private fun FolderNeighbourList.ids() = (state.content as ChatListContent.Content).items.map { it.id }

    private companion object {
        const val WORK = 1
        const val FAMILY = 2
    }
}
