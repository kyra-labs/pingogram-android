package dev.kyralabs.pingogram.feature.profile.chatinfo

import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibJoinRequestRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.feature.profile.chatinfo.requests.JoinRequestsEffect
import dev.kyralabs.pingogram.feature.profile.chatinfo.requests.JoinRequestsEvent
import dev.kyralabs.pingogram.feature.profile.chatinfo.requests.JoinRequestsUiState
import dev.kyralabs.pingogram.feature.profile.chatinfo.requests.JoinRequestsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/** The join requests page on the real TDLib repositories. */
@OptIn(ExperimentalCoroutinesApi::class)
class JoinRequestsViewModelTest : GroupViewModelTest() {
    private fun request(
        userId: Long,
        bio: String = "",
    ) = TdApi.ChatJoinRequest(userId, NOW.epochSecond.toInt(), bio)

    private fun answer(
        total: Int,
        vararg requests: TdApi.ChatJoinRequest,
    ) = client.on(TdApi.GetChatJoinRequests::class, TdResult.Success(TdApi.ChatJoinRequests(total, arrayOf(*requests))))

    private suspend fun TestScope.viewModel(): JoinRequestsViewModel {
        val users = TdlibUserRepository(client, backgroundScope)
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        client.on(TdApi.GetChat::class, TdResult.Success(chat(isChannel = true)))
        runCurrent()
        client.emit(TdApiFixtures.updateUser(TdApiFixtures.user(id = ADA, firstName = "Ada")))
        client.emit(TdApi.UpdateNewChat(chat(isChannel = true)))
        runCurrent()
        val viewModel =
            JoinRequestsViewModel(
                CHAT,
                TdlibJoinRequestRepository(client),
                chats,
                users,
                clock,
                patterns,
                { Locale.US },
            )
        created += viewModel
        runCurrent()
        return viewModel
    }

    private val JoinRequestsViewModel.content get() = uiState.value as JoinRequestsUiState.Content

    @Test
    fun `lists the requests with names, bios and dates`() =
        runTest {
            answer(1, request(ADA, bio = "Rustacean"))

            val content = viewModel().content

            assertTrue(content.isChannel)
            assertEquals(1, content.totalCount)
            val item = content.requests.single()
            assertEquals("Rustacean" to "Oct 22", item.bio to item.date)
        }

    @Test
    fun `an approved request leaves the list with a snackbar`() =
        runTest {
            answer(2, request(ADA), request(7))
            client.on(TdApi.ProcessChatJoinRequest::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()

            viewModel.onEvent(JoinRequestsEvent.Approve(ADA))
            runCurrent()

            assertEquals(listOf(7L), viewModel.content.requests.map { it.userId })
            assertEquals(1, viewModel.content.totalCount)
            assertTrue(viewModel.effects.first() is JoinRequestsEffect.Approved)
        }

    @Test
    fun `a refused answer keeps the request and says why`() =
        runTest {
            answer(1, request(ADA))
            client.on(TdApi.ProcessChatJoinRequest::class, TdResult.Failure(TdError(400, "HIDE_REQUESTER_MISSING")))
            val viewModel = viewModel()

            viewModel.onEvent(JoinRequestsEvent.Dismiss(ADA))
            runCurrent()

            assertEquals(1, viewModel.content.requests.size)
            assertTrue(viewModel.content.busy.isEmpty())
            assertTrue(viewModel.effects.first() is JoinRequestsEffect.ShowError)
        }

    @Test
    fun `dismissing all asks first`() =
        runTest {
            answer(2, request(ADA), request(7))
            client.on(TdApi.ProcessChatJoinRequests::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()

            viewModel.onEvent(JoinRequestsEvent.DismissAll)
            assertTrue(viewModel.content.dismissAllDialog)
            assertTrue(client.sentOf<TdApi.ProcessChatJoinRequests>().isEmpty())

            viewModel.onEvent(JoinRequestsEvent.ConfirmDismissAll)
            runCurrent()

            assertFalse(client.sentOf<TdApi.ProcessChatJoinRequests>().single().approve)
            assertTrue(viewModel.content.requests.isEmpty())
        }

    @Test
    fun `loads the next page after the last request`() =
        runTest {
            answer(2, request(ADA))
            val viewModel = viewModel()
            answer(2, request(7))

            viewModel.onEvent(JoinRequestsEvent.LoadMore)
            runCurrent()

            assertEquals(listOf(ADA, 7L), viewModel.content.requests.map { it.userId })
            assertEquals(
                ADA,
                client
                    .sentOf<TdApi.GetChatJoinRequests>()
                    .last()
                    .offsetRequest.userId,
            )
            assertFalse(viewModel.content.hasMore)
        }

    @Test
    fun `requests that cannot be read show the error`() =
        runTest {
            client.on(TdApi.GetChatJoinRequests::class, TdResult.Failure(TdError(400, "CHAT_ADMIN_REQUIRED")))

            assertTrue(viewModel().uiState.value is JoinRequestsUiState.Error)
        }
}
