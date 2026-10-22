package dev.kyralabs.pingogram.feature.profile.chatinfo

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibChatBoostRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The boosts page, read only. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatBoostViewModelTest : GroupViewModelTest() {
    private fun status() =
        TdApi.ChatBoostStatus("https://t.me/rustberlin?boost", intArrayOf(), 2, 0, 14, 10, 20, 30, 1.5, emptyArray())

    private fun TestScope.viewModel(
        status: TdResult<TdApi.ChatBoostStatus> = TdResult.Success(status()),
        isChannel: Boolean = true,
    ): ChatBoostViewModel {
        client.on(TdApi.GetChat::class, TdResult.Success(chat(isChannel)))
        client.on(TdApi.GetChatBoostStatus::class, status)
        client.on(TdApi.GetAvailableChatBoostSlots::class, TdResult.Success(TdApi.ChatBoostSlots(emptyArray())))
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        runCurrent()
        val viewModel = ChatBoostViewModel(CHAT, TdlibChatBoostRepository(client), chats)
        created += viewModel
        return viewModel
    }

    @Test
    fun `loads the boosts after the first frame`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(ChatBoostUiState.Loading, viewModel.uiState.value)

            runCurrent()

            val content = viewModel.uiState.value as ChatBoostUiState.Content
            assertEquals(2, content.status.level)
            assertEquals(6, content.status.boostsToNextLevel)
            assertTrue(content.isChannel)
            assertTrue(client.sentOf<TdApi.BoostChat>().isEmpty())
        }

    @Test
    fun `a group says so`() =
        runTest {
            val viewModel = viewModel(isChannel = false)
            runCurrent()

            assertEquals(false, (viewModel.uiState.value as ChatBoostUiState.Content).isChannel)
        }

    @Test
    fun `a failure shows, and loads again on retry`() =
        runTest {
            val viewModel = viewModel(TdResult.Failure(TdError(500, "Network is unreachable")))
            runCurrent()
            assertEquals(ChatBoostUiState.Error(DomainError.Network), viewModel.uiState.value)

            client.on(TdApi.GetChatBoostStatus::class, TdResult.Success(status()))
            viewModel.onEvent(ChatBoostEvent.Retry)
            runCurrent()

            assertTrue(viewModel.uiState.value is ChatBoostUiState.Content)
        }
}
