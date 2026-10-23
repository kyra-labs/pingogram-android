package dev.kyralabs.pingogram.feature.settings.ui.folders

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibChatFolderRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.domain.model.ChatFolderIncludedType
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatFoldersViewModelTest {
    private val client = FakeTdlibClient()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun folder(
        title: String = "Work",
        includeGroups: Boolean = false,
        includedChats: LongArray = longArrayOf(1, 2),
        shareable: Boolean = false,
    ) = TdApi.ChatFolder(
        TdApi.ChatFolderName(TdApi.FormattedText(title, emptyArray()), true),
        TdApi.ChatFolderIcon("Custom"),
        -1,
        shareable,
        longArrayOf(),
        includedChats,
        longArrayOf(),
        false,
        false,
        false,
        false,
        false,
        false,
        includeGroups,
        false,
    )

    private val recommended =
        TdApi.RecommendedChatFolders(
            arrayOf(TdApi.RecommendedChatFolder(folder("Unread", includeGroups = true), "New messages")),
        )

    // Through the real TDLib repositories on FakeTdlibClient; the chat repository subscribes before TDLib announces the
    // folders, as at app start.
    private suspend fun TestScope.viewModel(
        folders: Array<TdApi.ChatFolderInfo> =
            arrayOf(
                TdApiFixtures.chatFolderInfo(id = 2, title = "Work"),
                TdApiFixtures.chatFolderInfo(id = 3, title = "Family"),
            ),
        mainChatListPosition: Int = 0,
    ): ChatFoldersViewModel {
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        client.emit(TdApiFixtures.updateChatFolders(*folders, mainChatListPosition = mainChatListPosition))
        runCurrent()
        return ChatFoldersViewModel(chats, TdlibChatFolderRepository(client))
    }

    private fun answerFolders() {
        client.on(TdApi.GetChatFolder::class) { request ->
            TdResult.Success(
                if (request.chatFolderId ==
                    2
                ) {
                    folder()
                } else {
                    folder("Family", includeGroups = true, includedChats = longArrayOf())
                },
            )
        }
        client.on(TdApi.GetRecommendedChatFolders::class, TdResult.Success(recommended))
    }

    @Test
    fun `loads the folders in their order with what they include`() =
        runTest {
            answerFolders()
            val viewModel = viewModel()
            runCurrent()

            val state = viewModel.uiState.value as ChatFoldersUiState.Content
            assertEquals(listOf(2, 3), state.folders.map { it.id })
            assertEquals(2, state.folders[0].chatCount)
            assertEquals(setOf(ChatFolderIncludedType.Groups), state.folders[1].includedTypes)
            assertEquals(listOf("Unread"), state.recommended.map { it.title })
        }

    @Test
    fun `a folder that cannot be read shows the error, and Retry reads again`() =
        runTest {
            client.on(TdApi.GetChatFolder::class, TdResult.Failure(TdError(500, "Network")))
            client.on(TdApi.GetRecommendedChatFolders::class, TdResult.Success(recommended))
            val viewModel = viewModel()
            runCurrent()
            assertInstanceOf(ChatFoldersUiState.Error::class.java, viewModel.uiState.value)

            answerFolders()
            viewModel.onEvent(ChatFoldersEvent.Retry)
            runCurrent()

            assertInstanceOf(ChatFoldersUiState.Content::class.java, viewModel.uiState.value)
        }

    @Test
    fun `without suggestions the page shows the user's folders alone`() =
        runTest {
            answerFolders()
            client.on(TdApi.GetRecommendedChatFolders::class, TdResult.Failure(TdError(500, "Network")))
            val viewModel = viewModel()
            runCurrent()

            assertTrue((viewModel.uiState.value as ChatFoldersUiState.Content).recommended.isEmpty())
        }

    @Test
    fun `a new order shows at once and goes to Telegram with the main list kept in place`() =
        runTest {
            answerFolders()
            client.on(TdApi.ReorderChatFolders::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel(mainChatListPosition = 1)
            runCurrent()

            viewModel.onEvent(ChatFoldersEvent.Reorder(listOf(3, 2)))

            assertEquals(listOf(3, 2), (viewModel.uiState.value as ChatFoldersUiState.Content).folders.map { it.id })
            runCurrent()
            val sent = client.sentOf<TdApi.ReorderChatFolders>().single()
            assertArrayEquals(intArrayOf(3, 2), sent.chatFolderIds)
            assertEquals(1, sent.mainChatListPosition)
        }

    @Test
    fun `screen readers move a folder one place, and not past the ends`() =
        runTest {
            answerFolders()
            client.on(TdApi.ReorderChatFolders::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            runCurrent()

            viewModel.onEvent(ChatFoldersEvent.Move(2, up = true))
            viewModel.onEvent(ChatFoldersEvent.Move(2, up = false))
            runCurrent()

            assertEquals(listOf(3, 2), (viewModel.uiState.value as ChatFoldersUiState.Content).folders.map { it.id })
            assertEquals(1, client.sentOf<TdApi.ReorderChatFolders>().size)
        }

    @Test
    fun `a refused order returns to Telegram's with a snackbar`() =
        runTest {
            answerFolders()
            client.on(TdApi.ReorderChatFolders::class, TdResult.Failure(TdError(500, "Network")))
            val viewModel = viewModel()
            runCurrent()

            viewModel.effects.test {
                viewModel.onEvent(ChatFoldersEvent.Reorder(listOf(3, 2)))
                runCurrent()

                assertEquals(ChatFoldersEffect.ShowError(DomainError.Network), awaitItem())
            }
            assertEquals(listOf(2, 3), (viewModel.uiState.value as ChatFoldersUiState.Content).folders.map { it.id })
        }

    @Test
    fun `adds a suggested folder as Telegram suggests it`() =
        runTest {
            answerFolders()
            client.on(
                TdApi.CreateChatFolder::class,
                TdResult.Success(TdApiFixtures.chatFolderInfo(id = 4, title = "Unread")),
            )
            val viewModel = viewModel()
            runCurrent()

            viewModel.onEvent(ChatFoldersEvent.AddRecommended(0))

            assertTrue((viewModel.uiState.value as ChatFoldersUiState.Content).recommended.single().isAdding)
            runCurrent()
            val sent = client.sentOf<TdApi.CreateChatFolder>().single().folder
            assertEquals("Unread", sent.name.text.text)
            assertTrue(sent.includeGroups)
            assertEquals(2, client.sentOf<TdApi.GetRecommendedChatFolders>().size)
        }

    @Test
    fun `too many folders says so`() =
        runTest {
            answerFolders()
            client.on(TdApi.CreateChatFolder::class, TdResult.Failure(TdError(400, "CHATLISTS_TOO_MUCH")))
            val viewModel = viewModel()
            runCurrent()

            viewModel.effects.test {
                viewModel.onEvent(ChatFoldersEvent.AddRecommended(0))
                runCurrent()

                assertEquals(ChatFoldersEffect.ShowError(DomainError.TooManyChatFolders), awaitItem())
            }
            assertTrue(!(viewModel.uiState.value as ChatFoldersUiState.Content).recommended.single().isAdding)
        }
}
