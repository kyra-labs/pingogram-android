package dev.kyralabs.pingogram.feature.settings.ui.twostep

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibTwoStepVerificationRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.feature.settings.ui.plainSpaces
import dev.kyralabs.pingogram.feature.settings.ui.testTimeFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationFlowEvent as Event

@OptIn(ExperimentalCoroutinesApi::class)
class TwoStepVerificationFlowViewModelTest {
    private val client = FakeTdlibClient()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        client.on(TdApi.GetPasswordState::class, TdResult.Success(passwordState(hint = "First pet")))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Through the real TDLib repository on FakeTdlibClient, as the app wires it.
    private fun TestScope.viewModel(flow: TwoStepVerificationFlow) =
        TwoStepVerificationFlowViewModel(flow, TdlibTwoStepVerificationRepository(client), testTimeFormats())
            .also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: TwoStepVerificationFlowViewModel): List<TwoStepVerificationFlowEffect> {
        val effects = mutableListOf<TwoStepVerificationFlowEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun passwordState(
        hasPassword: Boolean = true,
        hint: String = "",
        pendingEmail: String? = null,
    ) = TdApi.PasswordState(
        hasPassword,
        hint,
        false,
        false,
        pendingEmail?.let { TdApi.EmailAddressAuthenticationCodeInfo(it, 6) },
        "",
        0,
    )

    private fun TestScope.send(
        viewModel: TwoStepVerificationFlowViewModel,
        event: Event,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    private val TwoStepVerificationFlowViewModel.state get() = uiState.value

    private fun finished(result: TwoStepVerificationResult) = TwoStepVerificationFlowEffect.Finished(result)

    @Test
    fun `setting a password starts at the new password and shows no hint`() =
        runTest {
            val viewModel = viewModel(TwoStepVerificationFlow.SetPassword)

            assertEquals(TwoStepVerificationStep.NewPassword, viewModel.state.step)
            assertFalse(viewModel.state.canGoBack)
            assertEquals("", viewModel.state.passwordHint)
            assertTrue(client.sentOf<TdApi.GetPasswordState>().isEmpty())
        }

    @Test
    fun `sets a password with a recovery email and confirms its code`() =
        runTest {
            client.on(TdApi.SetPassword::class, TdResult.Success(passwordState(false, pendingEmail = "a***@e***.com")))
            client.on(TdApi.CheckRecoveryEmailAddressCode::class, TdResult.Success(passwordState()))
            val viewModel = viewModel(TwoStepVerificationFlow.SetPassword)
            val effects = effectsOf(viewModel)

            send(viewModel, Event.SubmitNewPassword("secret", "secret"))
            assertEquals(TwoStepVerificationStep.Hint, viewModel.state.step)
            send(viewModel, Event.SubmitHint(" pet "))
            assertEquals(TwoStepVerificationStep.RecoveryEmail, viewModel.state.step)
            send(viewModel, Event.SubmitRecoveryEmail("a@example.com "))
            assertEquals(TwoStepVerificationStep.EmailCode, viewModel.state.step)
            assertEquals("a***@e***.com", viewModel.state.emailPattern)
            send(viewModel, Event.SubmitEmailCode("123456"))

            val sent = client.sentOf<TdApi.SetPassword>().single()
            assertEquals(
                listOf("", "secret", "pet", "a@example.com"),
                listOf(sent.oldPassword, sent.newPassword, sent.newHint, sent.newRecoveryEmailAddress),
            )
            assertEquals("123456", client.sentOf<TdApi.CheckRecoveryEmailAddressCode>().single().code)
            assertEquals(listOf(finished(TwoStepVerificationResult.PasswordSet)), effects)
        }

    @Test
    fun `skipping the recovery email sets the password at once`() =
        runTest {
            client.on(TdApi.SetPassword::class, TdResult.Success(passwordState()))
            val viewModel = viewModel(TwoStepVerificationFlow.SetPassword)
            val effects = effectsOf(viewModel)

            send(viewModel, Event.SubmitNewPassword("secret", "secret"))
            send(viewModel, Event.SubmitHint(""))
            send(viewModel, Event.SkipRecoveryEmail)

            assertFalse(client.sentOf<TdApi.SetPassword>().single().setRecoveryEmailAddress)
            assertEquals(listOf(finished(TwoStepVerificationResult.PasswordSet)), effects)
        }

    @Test
    fun `different passwords stay on the step with an error`() =
        runTest {
            val viewModel = viewModel(TwoStepVerificationFlow.SetPassword)
            val effects = effectsOf(viewModel)

            send(viewModel, Event.SubmitNewPassword("secret", "secrte"))

            assertEquals(TwoStepVerificationStep.NewPassword, viewModel.state.step)
            assertEquals(TwoStepVerificationFieldError.PasswordsDiffer, viewModel.state.fieldError)
            assertEquals(listOf(TwoStepVerificationFlowEffect.InputRejected), effects)

            send(viewModel, Event.InputChanged)
            assertNull(viewModel.state.fieldError)
        }

    @Test
    fun `the hint cannot be the password`() =
        runTest {
            val viewModel = viewModel(TwoStepVerificationFlow.SetPassword)
            send(viewModel, Event.SubmitNewPassword("secret", "secret"))

            send(viewModel, Event.SubmitHint("secret"))

            assertEquals(TwoStepVerificationStep.Hint, viewModel.state.step)
            assertEquals(TwoStepVerificationFieldError.HintIsPassword, viewModel.state.fieldError)
        }

    @Test
    fun `changing the password checks the current one first and shows its hint`() =
        runTest {
            client.on(TdApi.GetRecoveryEmailAddress::class, TdResult.Success(TdApi.RecoveryEmailAddress("")))
            client.on(TdApi.SetPassword::class, TdResult.Success(passwordState()))
            val viewModel = viewModel(TwoStepVerificationFlow.ChangePassword)
            val effects = effectsOf(viewModel)
            assertEquals(TwoStepVerificationStep.CurrentPassword, viewModel.state.step)
            assertEquals("First pet", viewModel.state.passwordHint)

            send(viewModel, Event.SubmitCurrentPassword("old"))
            send(viewModel, Event.SubmitNewPassword("new", "new"))
            send(viewModel, Event.SubmitHint(""))

            assertEquals("old", client.sentOf<TdApi.GetRecoveryEmailAddress>().single().password)
            val sent = client.sentOf<TdApi.SetPassword>().single()
            assertEquals(listOf("old", "new"), listOf(sent.oldPassword, sent.newPassword))
            assertFalse(sent.setRecoveryEmailAddress)
            assertEquals(listOf(finished(TwoStepVerificationResult.PasswordChanged)), effects)
        }

    @Test
    fun `a wrong current password shows at the field and buzzes`() =
        runTest {
            client.on(TdApi.GetRecoveryEmailAddress::class, TdResult.Failure(TdError(400, "PASSWORD_HASH_INVALID")))
            val viewModel = viewModel(TwoStepVerificationFlow.ChangePassword)
            val effects = effectsOf(viewModel)

            send(viewModel, Event.SubmitCurrentPassword("wrong"))

            assertEquals(TwoStepVerificationStep.CurrentPassword, viewModel.state.step)
            assertEquals(
                TwoStepVerificationFieldError.Rejected(DomainError.PasswordInvalid),
                viewModel.state.fieldError,
            )
            assertFalse(viewModel.state.isWorking)
            assertEquals(listOf(TwoStepVerificationFlowEffect.InputRejected), effects)
        }

    @Test
    fun `turning off removes the password with the current one`() =
        runTest {
            client.on(TdApi.SetPassword::class, TdResult.Success(passwordState(hasPassword = false)))
            val viewModel = viewModel(TwoStepVerificationFlow.RemovePassword)
            val effects = effectsOf(viewModel)

            send(viewModel, Event.SubmitCurrentPassword("old"))

            val sent = client.sentOf<TdApi.SetPassword>().single()
            assertEquals(listOf("old", ""), listOf(sent.oldPassword, sent.newPassword))
            assertEquals(listOf(finished(TwoStepVerificationResult.PasswordRemoved)), effects)
        }

    @Test
    fun `changes the recovery email and resends its code`() =
        runTest {
            val pending = passwordState(pendingEmail = "b***@e***.com")
            client.on(TdApi.GetRecoveryEmailAddress::class, TdResult.Success(TdApi.RecoveryEmailAddress("a@e.com")))
            client.on(TdApi.SetRecoveryEmailAddress::class, TdResult.Success(pending))
            client.on(TdApi.ResendRecoveryEmailAddressCode::class, TdResult.Success(pending))
            client.on(TdApi.CheckRecoveryEmailAddressCode::class, TdResult.Success(passwordState()))
            val viewModel = viewModel(TwoStepVerificationFlow.ChangeRecoveryEmail)
            val effects = effectsOf(viewModel)

            send(viewModel, Event.SubmitCurrentPassword("pw"))
            assertEquals(TwoStepVerificationStep.RecoveryEmail, viewModel.state.step)
            send(viewModel, Event.SubmitRecoveryEmail("b@example.com"))
            send(viewModel, Event.ResendEmailCode)
            send(viewModel, Event.SubmitEmailCode("654321"))

            val sent = client.sentOf<TdApi.SetRecoveryEmailAddress>().single()
            assertEquals(listOf("pw", "b@example.com"), listOf(sent.password, sent.newRecoveryEmailAddress))
            assertEquals(
                listOf(
                    TwoStepVerificationFlowEffect.CodeResent,
                    finished(TwoStepVerificationResult.RecoveryEmailConfirmed),
                ),
                effects,
            )
        }

    @Test
    fun `confirming a waiting recovery email shows where the code went`() =
        runTest {
            client.on(TdApi.GetPasswordState::class, TdResult.Success(passwordState(pendingEmail = "a***@e***.com")))
            client.on(TdApi.CheckRecoveryEmailAddressCode::class, TdResult.Failure(TdError(400, "CODE_INVALID")))
            val viewModel = viewModel(TwoStepVerificationFlow.ConfirmRecoveryEmail)

            assertEquals(TwoStepVerificationStep.EmailCode, viewModel.state.step)
            assertEquals("a***@e***.com", viewModel.state.emailPattern)
            send(viewModel, Event.SubmitEmailCode("000000"))
            assertEquals(TwoStepVerificationFieldError.Rejected(DomainError.CodeInvalid), viewModel.state.fieldError)
        }

    @Test
    fun `a forgotten password is recovered by email`() =
        runTest {
            client.on(
                TdApi.RequestPasswordRecovery::class,
                TdResult.Success(TdApi.EmailAddressAuthenticationCodeInfo("a***@e***.com", 6)),
            )
            client.on(TdApi.CheckPasswordRecoveryCode::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.RecoverPassword::class, TdResult.Success(passwordState()))
            val viewModel = viewModel(TwoStepVerificationFlow.ChangePassword)
            val effects = effectsOf(viewModel)

            send(viewModel, Event.ForgotPassword)
            assertEquals(TwoStepVerificationStep.RecoveryCode, viewModel.state.step)
            assertEquals("a***@e***.com", viewModel.state.emailPattern)
            send(viewModel, Event.SubmitRecoveryCode("111111"))
            assertTrue(viewModel.state.recovering)
            send(viewModel, Event.SubmitNewPassword("new", "new"))
            send(viewModel, Event.SubmitHint("hint"))

            val sent = client.sentOf<TdApi.RecoverPassword>().single()
            assertEquals(listOf("111111", "new", "hint"), listOf(sent.recoveryCode, sent.newPassword, sent.newHint))
            assertTrue(client.sentOf<TdApi.SetPassword>().isEmpty())
            assertEquals(listOf(finished(TwoStepVerificationResult.PasswordRecovered)), effects)
        }

    @Test
    fun `without a recovery email a forgotten password offers the reset`() =
        runTest {
            client.on(TdApi.RequestPasswordRecovery::class, TdResult.Failure(TdError(400, "PASSWORD_RECOVERY_NA")))
            client.on(TdApi.ResetPassword::class, TdResult.Success(TdApi.ResetPasswordResultPending(1_800_000_000)))
            val viewModel = viewModel(TwoStepVerificationFlow.RemovePassword)
            val effects = effectsOf(viewModel)

            send(viewModel, Event.ForgotPassword)
            assertEquals(TwoStepVerificationStep.Reset, viewModel.state.step)
            send(viewModel, Event.ConfirmReset)

            assertEquals(listOf(finished(TwoStepVerificationResult.ResetRequested)), effects)
        }

    @Test
    fun `no access to the email offers the reset, and a declined reset says until when`() =
        runTest {
            client.on(
                TdApi.RequestPasswordRecovery::class,
                TdResult.Success(TdApi.EmailAddressAuthenticationCodeInfo("a***@e***.com", 6)),
            )
            val retry = Instant.parse("2026-10-25T09:41:00Z").epochSecond.toInt()
            client.on(TdApi.ResetPassword::class, TdResult.Success(TdApi.ResetPasswordResultDeclined(retry)))
            val viewModel = viewModel(TwoStepVerificationFlow.ChangePassword)
            val effects = effectsOf(viewModel)

            send(viewModel, Event.ForgotPassword)
            send(viewModel, Event.NoAccessToEmail)
            send(viewModel, Event.ConfirmReset)

            assertEquals(TwoStepVerificationStep.Reset, viewModel.state.step)
            val declined = effects.single() as TwoStepVerificationFlowEffect.ResetDeclined
            assertEquals("Oct 25, 2026, 9:41 AM", plainSpaces(declined.retryDate))
        }

    @Test
    fun `back returns step by step and leaves the recovery`() =
        runTest {
            client.on(
                TdApi.RequestPasswordRecovery::class,
                TdResult.Success(TdApi.EmailAddressAuthenticationCodeInfo("a***@e***.com", 6)),
            )
            client.on(TdApi.CheckPasswordRecoveryCode::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel(TwoStepVerificationFlow.ChangePassword)
            send(viewModel, Event.ForgotPassword)
            send(viewModel, Event.SubmitRecoveryCode("111111"))

            send(viewModel, Event.Back)
            assertEquals(TwoStepVerificationStep.RecoveryCode, viewModel.state.step)
            assertFalse(viewModel.state.recovering)
            send(viewModel, Event.Back)
            assertEquals(TwoStepVerificationStep.CurrentPassword, viewModel.state.step)
            assertFalse(viewModel.state.canGoBack)
        }

    @Test
    fun `a flood wait counts down while the step waits`() =
        runTest {
            client.on(
                TdApi.GetRecoveryEmailAddress::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 3")),
            )
            val viewModel = viewModel(TwoStepVerificationFlow.ChangePassword)

            send(viewModel, Event.SubmitCurrentPassword("pw"))
            assertEquals(3L, viewModel.state.floodWaitSeconds)
            assertFalse(viewModel.state.canSubmit)
            send(viewModel, Event.SubmitCurrentPassword("pw"))
            assertEquals(1, client.sentOf<TdApi.GetRecoveryEmailAddress>().size)

            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(2L, viewModel.state.floodWaitSeconds)
            advanceTimeBy(2.seconds)
            runCurrent()
            assertNull(viewModel.state.floodWaitSeconds)
            assertTrue(viewModel.state.canSubmit)
        }

    @Test
    fun `other errors are snackbars`() =
        runTest {
            client.on(TdApi.GetRecoveryEmailAddress::class, TdResult.Failure(TdError(500, "Network is unreachable")))
            val viewModel = viewModel(TwoStepVerificationFlow.ChangePassword)
            val effects = effectsOf(viewModel)

            send(viewModel, Event.SubmitCurrentPassword("pw"))

            assertEquals(listOf(TwoStepVerificationFlowEffect.ShowError(DomainError.Network)), effects)
            assertNull(viewModel.state.fieldError)
        }
}
