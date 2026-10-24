package dev.kyralabs.pingogram.feature.settings.ui.folders

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibChatFolderRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.feature.settings.ui.folders.invite.FolderInviteEffect
import dev.kyralabs.pingogram.feature.settings.ui.folders.invite.FolderInviteEvent
import dev.kyralabs.pingogram.feature.settings.ui.folders.invite.FolderInviteUiState
import dev.kyralabs.pingogram.feature.settings.ui.folders.invite.FolderInviteViewModel
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FolderInviteViewModelTest {
    private val client = FakeTdlibClient()
    private val link = "https://t.me/addlist/abc"

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val design = TdApiFixtures.chat(id = 11, title = "Design team", type = TdApi.ChatTypeBasicGroup(5))
    private val news = TdApiFixtures.chat(id = 12, title = "News", type = TdApi.ChatTypeSupergroup(6, true))
    private val android = TdApiFixtures.chat(id = 13, title = "Android", type = TdApi.ChatTypeSupergroup(7, false))

    private fun info(
        folderId: Int = 0,
        missing: LongArray = longArrayOf(11, 12),
        added: LongArray = longArrayOf(13),
    ) = TdApi.ChatFolderInviteLinkInfo(TdApiFixtures.chatFolderInfo(id = folderId, title = "Work"), missing, added)

    private suspend fun TestScope.viewModel(): FolderInviteViewModel {
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        listOf(design, news, android).forEach { client.emit(TdApiFixtures.updateNewChat(it)) }
        runCurrent()
        return FolderInviteViewModel(link, TdlibChatFolderRepository(client), chats)
    }

    @Test
    fun `shows a new folder with the chats to join, all picked, and those the user is in`() =
        runTest {
            client.on(TdApi.CheckChatFolderInviteLink::class, TdResult.Success(info()))
            val viewModel = viewModel()
            runCurrent()

            val state = viewModel.uiState.value as FolderInviteUiState.Content
            assertEquals("Work", state.title)
            assertFalse(state.isAdded)
            assertEquals(listOf("Design team", "News"), state.newChats.map { it.title })
            assertTrue(state.newChats.all { it.picked })
            assertEquals(listOf(false, true), state.newChats.map { it.channel })
            assertEquals(listOf("Android"), state.joinedChats.map { it.title })
            assertEquals(link, client.sentOf<TdApi.CheckChatFolderInviteLink>().single().inviteLink)
        }

    @Test
    fun `adds a new folder with the picked chats and those the user is in, then closes`() =
        runTest {
            client.on(TdApi.CheckChatFolderInviteLink::class, TdResult.Success(info()))
            client.on(TdApi.AddChatFolderByInviteLink::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            runCurrent()

            viewModel.effects.test {
                viewModel.onEvent(FolderInviteEvent.TogglePicked(12))
                viewModel.onEvent(FolderInviteEvent.Add)
                runCurrent()

                assertEquals(FolderInviteEffect.Added, awaitItem())
            }
            val sent = client.sentOf<TdApi.AddChatFolderByInviteLink>().single()
            assertEquals(link, sent.inviteLink)
            assertArrayEquals(longArrayOf(11, 13), sent.chatIds)
        }

    @Test
    fun `a folder the user has joins only the new chats picked`() =
        runTest {
            client.on(
                TdApi.CheckChatFolderInviteLink::class,
                TdResult.Success(info(folderId = 4, missing = longArrayOf(12))),
            )
            client.on(TdApi.AddChatFolderByInviteLink::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            runCurrent()
            assertTrue((viewModel.uiState.value as FolderInviteUiState.Content).isAdded)

            viewModel.onEvent(FolderInviteEvent.Add)
            runCurrent()

            assertArrayEquals(longArrayOf(12), client.sentOf<TdApi.AddChatFolderByInviteLink>().single().chatIds)
        }

    @Test
    fun `nothing new to add has no Add, and nothing picked cannot be added`() =
        runTest {
            client.on(
                TdApi.CheckChatFolderInviteLink::class,
                TdResult.Success(info(folderId = 4, missing = longArrayOf(), added = longArrayOf(11, 12))),
            )
            val viewModel = viewModel()
            runCurrent()
            val added = viewModel.uiState.value as FolderInviteUiState.Content
            assertFalse(added.hasSomethingToAdd)

            client.on(
                TdApi.CheckChatFolderInviteLink::class,
                TdResult.Success(info(folderId = 4, missing = longArrayOf(12))),
            )
            val other = viewModel()
            runCurrent()
            other.onEvent(FolderInviteEvent.TogglePicked(12))
            other.onEvent(FolderInviteEvent.Add)
            runCurrent()

            assertFalse((other.uiState.value as FolderInviteUiState.Content).canAdd)
            assertTrue(client.sentOf<TdApi.AddChatFolderByInviteLink>().isEmpty())
        }

    @Test
    fun `a refusal shows under the chats and can be tried again`() =
        runTest {
            client.on(TdApi.CheckChatFolderInviteLink::class, TdResult.Success(info()))
            client.on(TdApi.AddChatFolderByInviteLink::class, TdResult.Failure(TdError(400, "CHATLISTS_TOO_MUCH")))
            val viewModel = viewModel()
            runCurrent()

            viewModel.onEvent(FolderInviteEvent.Add)
            runCurrent()

            val state = viewModel.uiState.value as FolderInviteUiState.Content
            assertEquals(DomainError.TooManyChatFolders, state.addError)
            assertFalse(state.isAdding)
            assertTrue(state.canAdd)
        }

    @Test
    fun `an expired link cannot be tried again, no network can`() =
        runTest {
            client.on(TdApi.CheckChatFolderInviteLink::class, TdResult.Failure(TdError(400, "INVITE_SLUG_EXPIRED")))
            val viewModel = viewModel()
            runCurrent()
            assertEquals(
                FolderInviteUiState.Failed(DomainError.InviteLinkInvalid, canRetry = false),
                viewModel.uiState.value,
            )

            client.on(TdApi.CheckChatFolderInviteLink::class, TdResult.Failure(TdError(500, "Network")))
            val offline = viewModel()
            runCurrent()
            assertEquals(FolderInviteUiState.Failed(DomainError.Network, canRetry = true), offline.uiState.value)

            client.on(TdApi.CheckChatFolderInviteLink::class, TdResult.Success(info()))
            offline.onEvent(FolderInviteEvent.Retry)
            runCurrent()
            assertTrue(offline.uiState.value is FolderInviteUiState.Content)
        }
}
