package dev.kyralabs.pingogram.feature.settings.ui.twostep

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibTwoStepVerificationRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.feature.settings.ui.TEST_NOW
import dev.kyralabs.pingogram.feature.settings.ui.plainSpaces
import dev.kyralabs.pingogram.feature.settings.ui.testTimeFormats
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TwoStepVerificationViewModelTest {
    private val client = FakeTdlibClient()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        answer(passwordState())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Through the real TDLib repository on FakeTdlibClient, as the app wires it.
    private fun TestScope.viewModel() =
        TwoStepVerificationViewModel(
            TdlibTwoStepVerificationRepository(client),
            testTimeFormats(),
        ).also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: TwoStepVerificationViewModel): List<TwoStepVerificationEffect> {
        val effects = mutableListOf<TwoStepVerificationEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun answer(state: TdApi.PasswordState) = client.on(TdApi.GetPasswordState::class, TdResult.Success(state))

    private fun passwordState(
        hasPassword: Boolean = true,
        hasRecoveryEmail: Boolean = true,
        pendingEmail: String? = null,
        pendingResetDate: Int = 0,
    ) = TdApi.PasswordState(
        hasPassword,
        "",
        hasRecoveryEmail,
        false,
        pendingEmail?.let { TdApi.EmailAddressAuthenticationCodeInfo(it, 6) },
        "",
        pendingResetDate,
    )

    private val TwoStepVerificationViewModel.content get() = uiState.value as TwoStepVerificationUiState.Content

    @Test
    fun `is loading until the state is loaded`() =
        runTest {
            val viewModel = TwoStepVerificationViewModel(TdlibTwoStepVerificationRepository(client), testTimeFormats())

            assertEquals(TwoStepVerificationUiState.Loading, viewModel.uiState.value)
            runCurrent()
            assertEquals(
                TwoStepVerificationUiState.Content(hasPassword = true, hasRecoveryEmail = true),
                viewModel.content,
            )
        }

    @Test
    fun `shows a waiting recovery email and a waiting reset`() =
        runTest {
            val resetDate = Instant.parse("2026-10-25T09:41:00Z").epochSecond.toInt()
            answer(
                passwordState(hasRecoveryEmail = false, pendingEmail = "a***@e***.com", pendingResetDate = resetDate),
            )

            val content = viewModel().content

            assertEquals("a***@e***.com", content.pendingEmailPattern)
            assertEquals("Oct 25, 2026, 9:41 AM", content.pendingReset?.date?.let(::plainSpaces))
            assertEquals(false, content.pendingReset?.canCompleteNow)
        }

    @Test
    fun `a reset whose waiting period is over can complete`() =
        runTest {
            answer(passwordState(pendingResetDate = TEST_NOW.epochSecond.toInt() - 60))

            assertEquals(true, viewModel().content.pendingReset?.canCompleteNow)
        }

    @Test
    fun `an error offers to try again`() =
        runTest {
            client.on(TdApi.GetPasswordState::class, TdResult.Failure(TdError(500, "Network is unreachable")))
            val viewModel = viewModel()
            assertEquals(TwoStepVerificationUiState.Error(DomainError.Network), viewModel.uiState.value)

            answer(passwordState(hasPassword = false, hasRecoveryEmail = false))
            viewModel.onEvent(TwoStepVerificationEvent.Retry)
            assertEquals(TwoStepVerificationUiState.Loading, viewModel.uiState.value)
            runCurrent()

            assertEquals(TwoStepVerificationUiState.Content(false, false), viewModel.content)
        }

    @Test
    fun `refreshing keeps what shows if it fails`() =
        runTest {
            val viewModel = viewModel()
            client.on(TdApi.GetPasswordState::class, TdResult.Failure(TdError(500, "Network is unreachable")))

            viewModel.onEvent(TwoStepVerificationEvent.Refresh)
            runCurrent()

            assertEquals(
                TwoStepVerificationUiState.Content(hasPassword = true, hasRecoveryEmail = true),
                viewModel.content,
            )
        }

    @Test
    fun `refreshing shows what a flow changed`() =
        runTest {
            val viewModel = viewModel()
            answer(passwordState(hasPassword = false, hasRecoveryEmail = false))

            viewModel.onEvent(TwoStepVerificationEvent.Refresh)
            runCurrent()

            assertEquals(TwoStepVerificationUiState.Content(false, false), viewModel.content)
        }

    @Test
    fun `cancels a waiting recovery email`() =
        runTest {
            answer(passwordState(pendingEmail = "a***@e***.com"))
            client.on(TdApi.CancelRecoveryEmailAddressVerification::class, TdResult.Success(passwordState()))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(TwoStepVerificationEvent.CancelRecoveryEmail)
            assertEquals(true, viewModel.content.isWorking)
            runCurrent()

            assertEquals(TwoStepVerificationUiState.Content(true, true), viewModel.content)
            assertEquals(listOf(TwoStepVerificationEffect.RecoveryEmailCancelled), effects)
        }

    @Test
    fun `cancels a waiting reset and loads the state again`() =
        runTest {
            answer(passwordState(pendingResetDate = 1_900_000_000))
            client.on(TdApi.CancelPasswordReset::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            answer(passwordState())

            viewModel.onEvent(TwoStepVerificationEvent.CancelReset)
            runCurrent()

            assertEquals(null, viewModel.content.pendingReset)
            assertEquals(listOf(TwoStepVerificationEffect.ResetCancelled), effects)
        }

    @Test
    fun `completes a reset whose waiting period is over`() =
        runTest {
            answer(passwordState(pendingResetDate = TEST_NOW.epochSecond.toInt() - 60))
            client.on(TdApi.ResetPassword::class, TdResult.Success(TdApi.ResetPasswordResultOk()))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            answer(passwordState(hasPassword = false, hasRecoveryEmail = false))

            viewModel.onEvent(TwoStepVerificationEvent.CompleteReset)
            runCurrent()

            assertEquals(TwoStepVerificationUiState.Content(false, false), viewModel.content)
            assertEquals(listOf(TwoStepVerificationEffect.ResetDone), effects)
        }

    @Test
    fun `a failed action is a snackbar and keeps what shows`() =
        runTest {
            answer(passwordState(pendingResetDate = 1_900_000_000))
            client.on(TdApi.CancelPasswordReset::class, TdResult.Failure(TdError(420, "FLOOD_WAIT_30")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(TwoStepVerificationEvent.CancelReset)
            runCurrent()

            assertEquals(false, viewModel.content.isWorking)
            assertEquals(listOf(TwoStepVerificationEffect.ShowError(DomainError.FloodWait(30))), effects)
        }
}
