package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.KeywordAlertSettings
import dev.kyralabs.pingogram.core.domain.model.KeywordHit
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.folder
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The chat list's "Keyword alerts" tab (board 30): only while there are matches and the user asked for it. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListKeywordTabTest : ChatListViewModelHarness() {
    private fun showTab() {
        keywordAlerts.settings.value = KeywordAlertSettings(showInChatList = true)
    }

    private fun hits(vararg hits: KeywordHit) {
        keywordHits.hits.value = hits.toList()
    }

    @Test
    fun `the tab shows last, counting the chats with a match, only while asked for and with matches`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1, "Work"))
            chats.setChats(ChatListType.Main, chat(id = 2), chat(id = 3))
            hits(KeywordHit(chatId = 2, messageId = 10, word = "launch", date = 100))
            val viewModel = viewModel()
            assertEquals(2, viewModel.state.folderTabs.size)

            showTab()
            runCurrent()
            assertEquals(FolderTab.KeywordAlerts(chatCount = 1), viewModel.state.folderTabs.last())

            hits()
            runCurrent()
            assertEquals(2, viewModel.state.folderTabs.size)
        }

    @Test
    fun `without folders, the tab comes with All chats`() =
        runTest {
            showTab()
            chats.setChats(ChatListType.Main, chat(id = 2))
            hits(KeywordHit(chatId = 2, messageId = 10, word = "launch", date = 100))

            assertEquals(
                listOf(FolderTab.AllChats(unreadCount = 0), FolderTab.KeywordAlerts(chatCount = 1)),
                viewModel().state.folderTabs,
            )
        }

    @Test
    fun `selected, it lists each chat with a match once, the newest first, with its word, without locked chats`() =
        runTest {
            showTab()
            chats.setChats(ChatListType.Main, chat(id = 2), chat(id = 3), chat(id = 4), chat(id = 5))
            chats.setChats(ChatListType.Archive, chat(id = 6, lists = listOf(ChatListType.Archive)))
            chatLock.lock(setOf(5L))
            hits(
                KeywordHit(chatId = 3, messageId = 30, word = "launch", date = 100),
                KeywordHit(chatId = 6, messageId = 60, word = "0.9.4", date = 300),
                KeywordHit(chatId = 3, messageId = 31, word = "Pingogram", date = 200),
                KeywordHit(chatId = 5, messageId = 50, word = "launch", date = 400),
            )
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.SelectFolder(KEYWORD_ALERTS_FOLDER_ID))

            assertEquals(KEYWORD_ALERTS_FOLDER_ID, viewModel.state.selectedFolderId)
            assertEquals(listOf(6L, 3L), viewModel.ids())
            assertEquals(listOf("0.9.4", "Pingogram"), viewModel.items.map { it.keyword })
            assertNull(viewModel.state.archive)
        }

    @Test
    fun `a row opens its chat at its newest match, and other tabs' rows have no word and open the chat`() =
        runTest {
            showTab()
            chats.setChats(ChatListType.Main, chat(id = 2))
            hits(KeywordHit(chatId = 2, messageId = 10, word = "launch", date = 100))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatListEvent.ChatClicked(2))
            assertNull(viewModel.item(2).keyword)
            send(viewModel, ChatListEvent.SelectFolder(KEYWORD_ALERTS_FOLDER_ID), ChatListEvent.ChatClicked(2))

            assertEquals(listOf(ChatListEffect.OpenChat(2), ChatListEffect.OpenChat(2, messageId = 10)), effects)
        }

    @Test
    fun `once the matches are gone, the main list shows again`() =
        runTest {
            showTab()
            chats.setChats(ChatListType.Main, chat(id = 2), chat(id = 3))
            hits(KeywordHit(chatId = 2, messageId = 10, word = "launch", date = 100))
            val viewModel = viewModel()
            send(viewModel, ChatListEvent.SelectFolder(KEYWORD_ALERTS_FOLDER_ID))

            hits()
            runCurrent()

            assertNull(viewModel.state.selectedFolderId)
            assertEquals(listOf(2L, 3L), viewModel.ids())
        }
}
