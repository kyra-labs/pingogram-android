package dev.kyralabs.pingogram.feature.settings.ui.account

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibAccountRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibTwoStepVerificationRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {
    private val client = FakeTdlibClient()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        client.on(TdApi.GetAccountTtl::class, TdResult.Success(TdApi.AccountTtl(180)))
        answerPassword(hasPassword = true, hint = "First pet")
        client.on(TdApi.SetAccountTtl::class, TdResult.Success(TdApi.Ok()))
        client.on(TdApi.DeleteAccount::class, TdResult.Success(TdApi.Ok()))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Through the real TDLib repositories on FakeTdlibClient, as the app wires them.
    private fun TestScope.viewModel(): DeleteAccountViewModel =
        DeleteAccountViewModel(TdlibAccountRepository(client), TdlibTwoStepVerificationRepository(client))
            .also { runCurrent() }

    private fun answerPassword(
        hasPassword: Boolean,
        hint: String = "",
    ) = client.on(
        TdApi.GetPasswordState::class,
        TdResult.Success(TdApi.PasswordState(hasPassword, hint, false, false, null, "", 0)),
    )

    private val DeleteAccountViewModel.content get() = uiState.value as DeleteAccountUiState.Content

    @Test
    fun `is loading until the period and the password state are loaded`() =
        runTest {
            client.on(TdApi.GetAccountTtl::class) { CompletableDeferred<TdResult<TdApi.AccountTtl>>().await() }

            assertEquals(DeleteAccountUiState.Loading, viewModel().uiState.value)
        }

    @Test
    fun `shows the period, Telegram's choices and whether a password is needed`() =
        runTest {
            val content = viewModel().content

            assertEquals(180, content.ttlDays)
            assertEquals(listOf(30, 90, 180, 365, 548, 730), content.ttlOptions)
            assertTrue(content.hasPassword)
            assertEquals("First pet", content.passwordHint)
        }

    @Test
    fun `offers a period another app set among the choices`() =
        runTest {
            client.on(TdApi.GetAccountTtl::class, TdResult.Success(TdApi.AccountTtl(45)))

            assertEquals(listOf(30, 45, 90, 180, 365, 548, 730), viewModel().content.ttlOptions)
        }

    @Test
    fun `a failed load shows why and loads again on retry`() =
        runTest {
            client.on(TdApi.GetPasswordState::class, TdResult.Failure(TdError(500, "Internal")))
            val viewModel = viewModel()
            assertTrue(viewModel.uiState.value is DeleteAccountUiState.Error)

            answerPassword(hasPassword = false)
            viewModel.onEvent(DeleteAccountEvent.Retry)
            runCurrent()

            assertFalse(viewModel.content.hasPassword)
        }

    @Test
    fun `a chosen period shows at once and goes to Telegram`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(DeleteAccountEvent.SelectTtl(365))
            assertEquals(365, viewModel.content.ttlDays)
            runCurrent()

            assertEquals(
                365,
                client
                    .sentOf<TdApi.SetAccountTtl>()
                    .single()
                    .ttl.days,
            )
            assertFalse(viewModel.content.savingTtl)
        }

    @Test
    fun `a refused period returns to the previous one with a snackbar`() =
        runTest {
            client.on(TdApi.SetAccountTtl::class, TdResult.Failure(TdError(400, "TTL_DAYS_INVALID")))
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(DeleteAccountEvent.SelectTtl(30))
                runCurrent()

                assertTrue(awaitItem() is DeleteAccountEffect.ShowError)
            }
            assertEquals(180, viewModel.content.ttlDays)
        }

    @Test
    fun `deleting asks first and sends nothing until confirmed`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(DeleteAccountEvent.RequestDeletion)
            runCurrent()

            assertTrue(viewModel.content.confirming)
            assertTrue(client.sentOf<TdApi.DeleteAccount>().isEmpty())

            viewModel.onEvent(DeleteAccountEvent.DismissConfirmation)
            assertFalse(viewModel.content.confirming)
        }

    @Test
    fun `a confirmed deletion sends the reason and the password, then waits for the logout`() =
        runTest {
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(DeleteAccountEvent.RequestDeletion)
                viewModel.onEvent(DeleteAccountEvent.ConfirmDeletion(reason = "  Moving on ", password = "secret"))
                runCurrent()

                assertEquals(DeleteAccountEffect.Deleted, awaitItem())
            }
            val sent = client.sentOf<TdApi.DeleteAccount>().single()
            assertEquals("Moving on" to "secret", sent.reason to sent.password)
            assertTrue(viewModel.content.isDeleting)
        }

    @Test
    fun `without the password it needs, nothing is sent`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(DeleteAccountEvent.ConfirmDeletion(reason = "", password = ""))
            runCurrent()

            assertTrue(client.sentOf<TdApi.DeleteAccount>().isEmpty())
        }

    @Test
    fun `without 2-step verification, it deletes without a password`() =
        runTest {
            answerPassword(hasPassword = false)
            val viewModel = viewModel()

            viewModel.onEvent(DeleteAccountEvent.ConfirmDeletion(reason = "", password = ""))
            runCurrent()

            assertEquals("", client.sentOf<TdApi.DeleteAccount>().single().password)
        }

    @Test
    fun `a wrong password shows at its field with a buzz, and goes as it is edited`() =
        runTest {
            client.on(TdApi.DeleteAccount::class, TdResult.Failure(TdError(400, "PASSWORD_HASH_INVALID")))
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(DeleteAccountEvent.ConfirmDeletion(reason = "", password = "wrong"))
                runCurrent()

                assertEquals(DeleteAccountEffect.PasswordRejected, awaitItem())
            }
            assertEquals(DomainError.PasswordInvalid, viewModel.content.passwordError)
            assertFalse(viewModel.content.isDeleting)

            viewModel.onEvent(DeleteAccountEvent.PasswordChanged)
            assertEquals(null, viewModel.content.passwordError)
        }

    @Test
    fun `a flood wait counts down and blocks deleting until it ends`() =
        runTest {
            client.on(TdApi.DeleteAccount::class, TdResult.Failure(TdError(429, "FLOOD_WAIT_3")))
            val viewModel = viewModel()

            viewModel.onEvent(DeleteAccountEvent.ConfirmDeletion(reason = "", password = "secret"))
            runCurrent()
            assertEquals(3L, viewModel.content.floodWaitSeconds)
            assertFalse(viewModel.content.canDelete(passwordEntered = true))

            advanceTimeBy(3.seconds)
            runCurrent()
            assertEquals(null, viewModel.content.floodWaitSeconds)
            assertTrue(viewModel.content.canDelete(passwordEntered = true))
        }

    @Test
    fun `other failures are snackbars`() =
        runTest {
            client.on(TdApi.DeleteAccount::class, TdResult.Failure(TdError(500, "Internal")))
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(DeleteAccountEvent.ConfirmDeletion(reason = "", password = "secret"))
                runCurrent()

                assertTrue(awaitItem() is DeleteAccountEffect.ShowError)
            }
        }
}
