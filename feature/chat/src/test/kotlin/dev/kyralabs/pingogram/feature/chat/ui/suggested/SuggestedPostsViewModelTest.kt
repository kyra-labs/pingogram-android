package dev.kyralabs.pingogram.feature.chat.ui.suggested

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.SuggestedPostRepository
import dev.kyralabs.pingogram.feature.chat.ui.SuggestedPostEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** An administrator's answers to posts suggested in a channel's direct messages ([SuggestedPostsViewModel]). */
@OptIn(ExperimentalCoroutinesApi::class)
class SuggestedPostsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSuggestedPosts()
    private val savedState = SavedStateHandle()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(handle: SavedStateHandle = savedState) = SuggestedPostsViewModel(CHAT, handle, repository)

    @Test
    fun `approving asks first, then publishes only on the confirmation`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.onEvent(SuggestedPostEvent.Answer(MESSAGE, approve = true))
            assertEquals(SuggestedPostDialog(MESSAGE, approve = true), viewModel.dialog.value)
            assertTrue(repository.calls.isEmpty())

            viewModel.onEvent(SuggestedPostEvent.Confirm)
            runCurrent()

            assertEquals(listOf("approve:$MESSAGE"), repository.calls)
            assertNull(viewModel.dialog.value)
        }

    @Test
    fun `declining sends the comment typed`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.onEvent(SuggestedPostEvent.Answer(MESSAGE, approve = false))
            viewModel.comment.edit { replace(0, length, "Too long") }

            viewModel.onEvent(SuggestedPostEvent.Confirm)
            runCurrent()

            assertEquals(listOf("decline:$MESSAGE:Too long"), repository.calls)
        }

    @Test
    fun `while Telegram answers the question waits, and a refusal stays in it`() =
        runTest(dispatcher) {
            val answer = CompletableDeferred<Outcome<Unit>>()
            repository.answer = answer
            val viewModel = viewModel()
            viewModel.onEvent(SuggestedPostEvent.Answer(MESSAGE, approve = true))
            viewModel.onEvent(SuggestedPostEvent.Confirm)
            runCurrent()
            assertTrue(viewModel.dialog.value!!.isWorking)

            viewModel.onEvent(SuggestedPostEvent.Dismiss)
            assertTrue(viewModel.dialog.value != null)

            answer.complete(Outcome.Failure(DomainError.Network))
            runCurrent()
            assertEquals(SuggestedPostDialog(MESSAGE, true, error = DomainError.Network), viewModel.dialog.value)
        }

    @Test
    fun `the open question survives process death, and cancelling sends nothing`() =
        runTest(dispatcher) {
            viewModel().onEvent(SuggestedPostEvent.Answer(MESSAGE, approve = false))

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))
            assertEquals(SuggestedPostDialog(MESSAGE, approve = false), restored.dialog.value)

            restored.onEvent(SuggestedPostEvent.Dismiss)
            assertNull(restored.dialog.value)
            assertTrue(repository.calls.isEmpty())
        }

    private class FakeSuggestedPosts : SuggestedPostRepository {
        val calls = mutableListOf<String>()
        var answer = CompletableDeferred(Outcome.Success(Unit) as Outcome<Unit>)

        override suspend fun approve(
            chatId: Long,
            messageId: Long,
        ): Outcome<Unit> {
            calls += "approve:$messageId"
            return answer.await()
        }

        override suspend fun decline(
            chatId: Long,
            messageId: Long,
            comment: String,
        ): Outcome<Unit> {
            calls += "decline:$messageId:$comment"
            return answer.await()
        }
    }

    private companion object {
        const val CHAT = -1001L
        const val MESSAGE = 7L
    }
}
