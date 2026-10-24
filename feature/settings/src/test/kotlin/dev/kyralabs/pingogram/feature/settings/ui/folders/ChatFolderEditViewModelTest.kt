package dev.kyralabs.pingogram.feature.settings.ui.folders

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibChatFolderRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.domain.model.ChatFolderExcludedType
import dev.kyralabs.pingogram.core.domain.model.ChatFolderIncludedType
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatFolderEditViewModelTest {
    private val client = FakeTdlibClient()
    private val savedState = SavedStateHandle()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val ada = TdApiFixtures.chat(id = 1, title = "Ada")
    private val team = TdApiFixtures.chat(id = 2, title = "Design team", type = TdApi.ChatTypeBasicGroup(5))
    private val news = TdApiFixtures.chat(id = 3, title = "News", type = TdApi.ChatTypeSupergroup(6, true))

    /** Work: Ada pinned, the design team included, muted chats left out, with a custom emoji in its title. */
    private val work =
        TdApi.ChatFolder(
            TdApi.ChatFolderName(
                TdApi.FormattedText("Work", arrayOf(TdApi.TextEntity(0, 1, TdApi.TextEntityTypeCustomEmoji(9)))),
                true,
            ),
            TdApi.ChatFolderIcon("Custom"),
            3,
            false,
            longArrayOf(1),
            longArrayOf(2),
            longArrayOf(),
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
        )

    private suspend fun TestScope.viewModel(
        folderId: Int? = 7,
        handle: SavedStateHandle = savedState,
    ): ChatFolderEditViewModel {
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        listOf(ada, team, news).forEach { client.emit(TdApiFixtures.updateNewChat(it)) }
        runCurrent()
        return ChatFolderEditViewModel(folderId, handle, chats, TdlibChatFolderRepository(client))
    }

    private fun TestScope.content(viewModel: ChatFolderEditViewModel): ChatFolderEditUiState.Content {
        runCurrent()
        return viewModel.uiState.value as ChatFolderEditUiState.Content
    }

    @Test
    fun `shows a folder's name, icon, chats and kinds of chats`() =
        runTest {
            client.on(TdApi.GetChatFolder::class, TdResult.Success(work))

            val state = content(viewModel())

            assertFalse(state.isNew)
            assertEquals("Work", state.name)
            assertEquals("Custom", state.iconName)
            assertEquals(listOf("Ada", "Design team"), state.includedChats.map { it.title })
            assertEquals(setOf(ChatFolderExcludedType.Muted), state.excludedTypes)
            assertEquals(7, client.sentOf<TdApi.GetChatFolder>().single().chatFolderId)
        }

    @Test
    fun `a folder that cannot be read shows the error, and Retry reads again`() =
        runTest {
            client.on(TdApi.GetChatFolder::class, TdResult.Failure(TdError(500, "Network")))
            val viewModel = viewModel()
            runCurrent()
            assertEquals(ChatFolderEditUiState.Error(DomainError.Network), viewModel.uiState.value)

            client.on(TdApi.GetChatFolder::class, TdResult.Success(work))
            viewModel.onEvent(ChatFolderEditEvent.Retry)

            assertEquals("Work", content(viewModel).name)
        }

    @Test
    fun `a new folder starts empty and asks for nothing`() =
        runTest {
            val state = content(viewModel(folderId = null))

            assertTrue(state.isNew)
            assertEquals("", state.name)
            assertTrue(client.sentOf<TdApi.GetChatFolder>().isEmpty())
        }

    @Test
    fun `the name keeps at most 12 characters`() =
        runTest {
            val viewModel = viewModel(folderId = null)
            runCurrent()

            viewModel.onEvent(ChatFolderEditEvent.NameChanged("A very long folder name"))

            assertEquals("A very long ", content(viewModel).name)
        }

    @Test
    fun `Save asks for a name and something to include before Telegram`() =
        runTest {
            val viewModel = viewModel(folderId = null)
            runCurrent()

            viewModel.effects.test {
                viewModel.onEvent(ChatFolderEditEvent.Save)

                assertEquals(ChatFolderEditEffect.Rejected, awaitItem())
            }
            val state = content(viewModel)
            assertTrue(state.nameMissing && state.nothingIncluded)
            assertTrue(client.sentOf<TdApi.CreateChatFolder>().isEmpty())

            viewModel.onEvent(ChatFolderEditEvent.NameChanged("Bots"))
            viewModel.onEvent(ChatFolderEditEvent.ToggleIncludedType(ChatFolderIncludedType.Bots))
            val fixed = content(viewModel)
            assertFalse(fixed.nameMissing || fixed.nothingIncluded)
        }

    @Test
    fun `creates a new folder with what was picked`() =
        runTest {
            client.on(TdApi.CreateChatFolder::class, TdResult.Success(TdApiFixtures.chatFolderInfo(id = 9)))
            val viewModel = viewModel(folderId = null)
            runCurrent()

            viewModel.effects.test {
                viewModel.onEvent(ChatFolderEditEvent.NameChanged("Groups "))
                viewModel.onEvent(ChatFolderEditEvent.SelectIcon("Groups"))
                viewModel.onEvent(ChatFolderEditEvent.ToggleIncludedType(ChatFolderIncludedType.Groups))
                viewModel.onEvent(ChatFolderEditEvent.OpenPicker(FolderPickerSide.Excluded))
                viewModel.onEvent(ChatFolderEditEvent.TogglePickedChat(FolderChatItem(3, "News", null)))
                viewModel.onEvent(ChatFolderEditEvent.ClosePicker)
                viewModel.onEvent(ChatFolderEditEvent.Save)
                runCurrent()

                assertEquals(ChatFolderEditEffect.Done, awaitItem())
            }
            val sent = client.sentOf<TdApi.CreateChatFolder>().single().folder
            assertEquals("Groups", sent.name.text.text)
            assertEquals("Groups", sent.icon?.name)
            assertTrue(sent.includeGroups)
            assertArrayEquals(longArrayOf(3), sent.excludedChatIds)
        }

    @Test
    fun `editing keeps what the editor does not show`() =
        runTest {
            client.on(TdApi.GetChatFolder::class, TdResult.Success(work))
            client.on(TdApi.EditChatFolder::class, TdResult.Success(TdApiFixtures.chatFolderInfo(id = 7)))
            val viewModel = viewModel()
            runCurrent()

            viewModel.onEvent(ChatFolderEditEvent.SelectIcon(null))
            viewModel.onEvent(ChatFolderEditEvent.Save)
            runCurrent()

            val sent = client.sentOf<TdApi.EditChatFolder>().single()
            assertEquals(7, sent.chatFolderId)
            assertEquals(1, sent.folder.name.text.entities.size)
            assertEquals(3, sent.folder.colorId)
            assertArrayEquals(longArrayOf(1), sent.folder.pinnedChatIds)
            assertArrayEquals(longArrayOf(2), sent.folder.includedChatIds)
            assertNull(sent.folder.icon)
        }

    @Test
    fun `a chat picked for one side leaves the other, and a removed pinned chat is no longer pinned`() =
        runTest {
            client.on(TdApi.GetChatFolder::class, TdResult.Success(work))
            client.on(TdApi.EditChatFolder::class, TdResult.Success(TdApiFixtures.chatFolderInfo(id = 7)))
            val viewModel = viewModel()
            runCurrent()

            viewModel.onEvent(ChatFolderEditEvent.OpenPicker(FolderPickerSide.Excluded))
            viewModel.onEvent(ChatFolderEditEvent.TogglePickedChat(FolderChatItem(1, "Ada", null)))
            val state = content(viewModel)
            assertEquals(listOf("Design team"), state.includedChats.map { it.title })
            assertEquals(listOf("Ada"), state.excludedChats.map { it.title })

            viewModel.onEvent(ChatFolderEditEvent.ClosePicker)
            viewModel.onEvent(ChatFolderEditEvent.Save)
            runCurrent()
            val sent = client.sentOf<TdApi.EditChatFolder>().single().folder
            assertArrayEquals(longArrayOf(), sent.pinnedChatIds)
            assertArrayEquals(longArrayOf(1), sent.excludedChatIds)
        }

    @Test
    fun `the picker lists the chats that match, those of its side picked`() =
        runTest {
            client.on(TdApi.GetChatFolder::class, TdResult.Success(work))
            client.on(TdApi.LoadChats::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            backgroundScope.launch { viewModel.picker.collect {} }
            runCurrent()

            viewModel.onEvent(ChatFolderEditEvent.OpenPicker(FolderPickerSide.Included))
            runCurrent()
            val all = viewModel.picker.value!!
            assertEquals(
                setOf(1L, 2L),
                all.chats
                    .filter { it.selected }
                    .map { it.chatId }
                    .toSet(),
            )
            assertEquals(3, all.chats.size)

            viewModel.onEvent(ChatFolderEditEvent.PickerQueryChanged("news"))
            runCurrent()
            assertEquals(
                listOf("News"),
                viewModel.picker.value!!
                    .chats
                    .map { it.title },
            )

            viewModel.onEvent(ChatFolderEditEvent.ClosePicker)
            runCurrent()
            assertNull(viewModel.picker.value)
        }

    @Test
    fun `a refused save stays on the page with a snackbar`() =
        runTest {
            client.on(TdApi.GetChatFolder::class, TdResult.Success(work))
            client.on(TdApi.EditChatFolder::class, TdResult.Failure(TdError(400, "DIALOG_FILTERS_TOO_MUCH")))
            val viewModel = viewModel()
            runCurrent()

            viewModel.effects.test {
                viewModel.onEvent(ChatFolderEditEvent.Save)
                runCurrent()

                assertEquals(ChatFolderEditEffect.Rejected, awaitItem())
                assertEquals(ChatFolderEditEffect.ShowError(DomainError.TooManyChatFolders), awaitItem())
            }
            assertFalse(content(viewModel).isSaving)
        }

    @Test
    fun `deletes the folder after the question, its chats kept`() =
        runTest {
            client.on(TdApi.GetChatFolder::class, TdResult.Success(work))
            client.on(TdApi.DeleteChatFolder::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            runCurrent()

            viewModel.effects.test {
                viewModel.onEvent(ChatFolderEditEvent.RequestDelete)
                assertTrue(content(viewModel).confirmingDelete)
                viewModel.onEvent(ChatFolderEditEvent.ConfirmDelete)
                runCurrent()

                assertEquals(ChatFolderEditEffect.Done, awaitItem())
            }
            val sent = client.sentOf<TdApi.DeleteChatFolder>().single()
            assertEquals(7, sent.chatFolderId)
            assertArrayEquals(longArrayOf(), sent.leaveChatIds)
        }

    @Test
    fun `what was typed survives process death`() =
        runTest {
            client.on(TdApi.GetChatFolder::class, TdResult.Success(work))
            val viewModel = viewModel()
            runCurrent()
            viewModel.onEvent(ChatFolderEditEvent.NameChanged("Jobs"))
            viewModel.onEvent(ChatFolderEditEvent.ToggleIncludedType(ChatFolderIncludedType.Contacts))
            viewModel.onEvent(ChatFolderEditEvent.RemoveChat(FolderPickerSide.Included, 2))

            val restored = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any?>(it) })
            val state = content(viewModel(handle = restored))

            assertEquals("Jobs", state.name)
            assertEquals(setOf(ChatFolderIncludedType.Contacts), state.includedTypes)
            assertEquals(listOf("Ada"), state.includedChats.map { it.title })
        }
}
