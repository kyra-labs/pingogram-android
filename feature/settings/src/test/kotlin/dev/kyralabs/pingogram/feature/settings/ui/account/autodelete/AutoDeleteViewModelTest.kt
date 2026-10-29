package dev.kyralabs.pingogram.feature.settings.ui.account.autodelete

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibAccountRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoDeleteViewModelTest {
    private val client = FakeTdlibClient()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        answerTimer(0)
        client.on(TdApi.SetDefaultMessageAutoDeleteTime::class, TdResult.Success(TdApi.Ok()))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun answerTimer(seconds: Int) =
        client.on(TdApi.GetDefaultMessageAutoDeleteTime::class, TdResult.Success(TdApi.MessageAutoDeleteTime(seconds)))

    // Through the real TDLib repository on FakeTdlibClient, as the app wires it.
    private fun TestScope.viewModel() = AutoDeleteViewModel(TdlibAccountRepository(client)).also { runCurrent() }

    private val AutoDeleteViewModel.content get() = uiState.value as AutoDeleteUiState.Content

    @Test
    fun `offers Telegram's choices with the timer of new chats chosen`() =
        runTest {
            answerTimer(604_800)

            val content = viewModel().content

            assertEquals(604_800, content.seconds)
            assertEquals(listOf(0, 86_400, 604_800, 2_678_400), content.options)
        }

    @Test
    fun `a timer another app set is offered too`() =
        runTest {
            answerTimer(3 * 86_400)

            assertEquals(listOf(0, 86_400, 604_800, 2_678_400, 3 * 86_400), viewModel().content.options)
        }

    @Test
    fun `a choice is saved at once and shows while Telegram answers`() =
        runTest {
            val answer = CompletableDeferred<TdResult<TdApi.Ok>>()
            client.on(TdApi.SetDefaultMessageAutoDeleteTime::class) { answer.await() }
            val viewModel = viewModel()

            viewModel.onEvent(AutoDeleteEvent.Select(86_400))
            runCurrent()

            assertEquals(86_400, viewModel.content.seconds)
            assertTrue(viewModel.content.isSaving)
            answer.complete(TdResult.Success(TdApi.Ok()))
            runCurrent()
            assertEquals(
                86_400,
                client
                    .sentOf<TdApi.SetDefaultMessageAutoDeleteTime>()
                    .single()
                    .messageAutoDeleteTime.time,
            )
            assertEquals(AutoDeleteUiState.Content(86_400, viewModel.content.options), viewModel.content)
        }

    @Test
    fun `a refusal brings the previous timer back with a snackbar`() =
        runTest {
            client.on(TdApi.SetDefaultMessageAutoDeleteTime::class, TdResult.Failure(TdError(400, "Network error")))
            val viewModel = viewModel()
            val effects = mutableListOf<AutoDeleteEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            viewModel.onEvent(AutoDeleteEvent.Select(86_400))
            runCurrent()

            assertEquals(0, viewModel.content.seconds)
            assertEquals(1, effects.size)
            assertTrue((effects.single() as AutoDeleteEffect.ShowError).error != DomainError.Silent)
        }

    @Test
    fun `a failed load shows its error, and trying again loads`() =
        runTest {
            client.on(TdApi.GetDefaultMessageAutoDeleteTime::class, TdResult.Failure(TdError(500, "Internal")))
            val viewModel = viewModel()
            assertTrue(viewModel.uiState.value is AutoDeleteUiState.Error)

            answerTimer(0)
            viewModel.onEvent(AutoDeleteEvent.Retry)
            runCurrent()

            assertEquals(0, viewModel.content.seconds)
        }
}
