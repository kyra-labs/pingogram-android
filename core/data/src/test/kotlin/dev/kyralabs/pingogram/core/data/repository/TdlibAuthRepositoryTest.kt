package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class TdlibAuthRepositoryTest {
    private val client = FakeTdlibClient(authState = AuthState.WaitPhoneNumber)

    private fun TestScope.repository() = TdlibAuthRepository(client, StandardTestDispatcher(testScheduler))

    private fun ok() = TdResult.Success(TdApi.Ok())

    @Test
    fun `authStep starts with the current state, then follows TDLib's updates, Closed included`() =
        runTest {
            repository().authStep.test {
                assertEquals(AuthStep.WaitPhoneNumber, awaitItem())

                client.emitAuthorizationState(TdApiFixtures.waitCode())
                client.emitAuthorizationState(TdApiFixtures.loggingOut())
                client.emitAuthorizationState(TdApiFixtures.closed())
                client.emitAuthorizationState(TdApiFixtures.waitTdlibParameters())

                assertTrue(awaitItem() is AuthStep.WaitCode)
                assertEquals(AuthStep.LoggingOut, awaitItem())
                assertEquals(AuthStep.Closed, awaitItem())
                assertEquals(AuthStep.Initializing, awaitItem())
            }
        }

    @Test
    fun `authStep ignores other updates`() =
        runTest {
            repository().authStep.test {
                awaitItem()

                client.emitConnectionState(TdApi.ConnectionStateReady())
                client.emit(TdApi.UpdateOption("version", TdApi.OptionValueString("1.8.68")))

                expectNoEvents()
            }
        }

    @Test
    fun `setPhoneNumber sends the number with compliant code settings`() =
        runTest {
            client.on(TdApi.SetAuthenticationPhoneNumber::class, ok())

            val result = repository().setPhoneNumber("+999662")

            val sent = client.sentOf<TdApi.SetAuthenticationPhoneNumber>().single()
            val settings = sent.settings
            assertEquals(Outcome.Success(Unit), result)
            assertEquals("+999662", sent.phoneNumber)
            assertFalse(settings.allowFlashCall, "flash call")
            assertFalse(settings.allowMissedCall, "missed call")
            assertFalse(settings.isCurrentPhoneNumber, "current number")
            assertFalse(settings.allowSmsRetrieverApi, "SMS retriever")
            assertNull(settings.firebaseAuthenticationSettings, "Firebase")
            assertEquals(0, settings.authenticationTokens.size)
        }

    @Test
    fun `setPhoneNumber maps a rejected number`() =
        runTest {
            client.on(TdApi.SetAuthenticationPhoneNumber::class, TdResult.Failure(TdError(400, "PHONE_NUMBER_INVALID")))

            assertEquals(Outcome.Failure(DomainError.PhoneNumberInvalid), repository().setPhoneNumber("+1"))
        }

    @Test
    fun `a flood wait carries the waiting time`() =
        runTest {
            client.on(
                TdApi.CheckAuthenticationCode::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 30")),
            )

            assertEquals(Outcome.Failure(DomainError.FloodWait(30)), repository().checkCode("12345"))
        }

    @Test
    fun `a request without an answer in 30 seconds fails with Network`() =
        runTest {
            val never = CompletableDeferred<TdResult<TdApi.Ok>>()
            client.on(TdApi.CheckAuthenticationPassword::class) { never.await() }
            val repository = repository()

            val result = async { repository.checkPassword("secret") }
            advanceTimeBy(29.seconds)
            runCurrent()
            assertFalse(result.isCompleted)
            advanceTimeBy(1.seconds)
            runCurrent()

            assertEquals(Outcome.Failure(DomainError.Network), result.await())
        }

    @Test
    fun `resendCode asks for the next code type as a user request`() =
        runTest {
            client.on(TdApi.ResendAuthenticationCode::class, ok())

            assertEquals(Outcome.Success(Unit), repository().resendCode())
            assertTrue(
                client.sentOf<TdApi.ResendAuthenticationCode>().single().reason is TdApi.ResendCodeReasonUserRequest,
            )
        }

    @Test
    fun `checkCode sends the code`() =
        runTest {
            client.on(TdApi.CheckAuthenticationCode::class, ok())

            repository().checkCode("12345")

            assertEquals("12345", client.sentOf<TdApi.CheckAuthenticationCode>().single().code)
        }

    @Test
    fun `requestQrCode lists no other accounts`() =
        runTest {
            client.on(TdApi.RequestQrCodeAuthentication::class, ok())

            assertEquals(Outcome.Success(Unit), repository().requestQrCode())
            assertArrayEquals(LongArray(0), client.sentOf<TdApi.RequestQrCodeAuthentication>().single().otherUserIds)
        }

    @Test
    fun `cancelQrCode starts over by logging out the unauthorized session`() =
        runTest {
            client.on(TdApi.LogOut::class, ok())

            assertEquals(Outcome.Success(Unit), repository().cancelQrCode())
            assertEquals(1, client.sentOf<TdApi.LogOut>().size)
        }

    @Test
    fun `checkPassword sends the password`() =
        runTest {
            client.on(TdApi.CheckAuthenticationPassword::class, ok())

            repository().checkPassword("secret")

            assertEquals("secret", client.sentOf<TdApi.CheckAuthenticationPassword>().single().password)
        }

    @Test
    fun `password recovery requests, checks and uses the recovery code`() =
        runTest {
            client.on(TdApi.RequestAuthenticationPasswordRecovery::class, ok())
            client.on(TdApi.CheckAuthenticationPasswordRecoveryCode::class, ok())
            client.on(TdApi.RecoverAuthenticationPassword::class, ok())
            val repository = repository()

            repository.requestPasswordRecovery()
            repository.checkPasswordRecoveryCode("123456")
            repository.recoverPassword("123456", "new secret", "new hint")

            val recover = client.sentOf<TdApi.RecoverAuthenticationPassword>().single()
            assertEquals(1, client.sentOf<TdApi.RequestAuthenticationPasswordRecovery>().size)
            assertEquals("123456", client.sentOf<TdApi.CheckAuthenticationPasswordRecoveryCode>().single().recoveryCode)
            assertEquals(
                listOf("123456", "new secret", "new hint"),
                listOf(recover.recoveryCode, recover.newPassword, recover.newHint),
            )
        }

    @Test
    fun `register sends the names and keeps the join notification`() =
        runTest {
            client.on(TdApi.RegisterUser::class, ok())

            repository().register("Ada", "Lovelace")

            val sent = client.sentOf<TdApi.RegisterUser>().single()
            assertEquals(listOf("Ada", "Lovelace"), listOf(sent.firstName, sent.lastName))
            assertFalse(sent.disableNotification)
        }

    @Test
    fun `email login sets the address, checks the code and resets the address`() =
        runTest {
            client.on(TdApi.SetAuthenticationEmailAddress::class, ok())
            client.on(TdApi.CheckAuthenticationEmailCode::class, ok())
            client.on(TdApi.ResetAuthenticationEmailAddress::class, ok())
            val repository = repository()

            repository.setEmailAddress("ada@example.org")
            repository.checkEmailCode("654321")
            repository.resetEmailAddress()

            val code = client.sentOf<TdApi.CheckAuthenticationEmailCode>().single().code
            assertEquals("ada@example.org", client.sentOf<TdApi.SetAuthenticationEmailAddress>().single().emailAddress)
            assertEquals("654321", (code as TdApi.EmailAddressAuthenticationCode).code)
            assertEquals(1, client.sentOf<TdApi.ResetAuthenticationEmailAddress>().size)
        }

    @Test
    fun `a pending email reset is reported as already requested`() =
        runTest {
            client.on(
                TdApi.ResetAuthenticationEmailAddress::class,
                TdResult.Failure(TdError(400, "TASK_ALREADY_EXISTS")),
            )

            assertEquals(Outcome.Failure(DomainError.AlreadyRequested), repository().resetEmailAddress())
        }

    @Test
    fun `logOut sends logOut`() =
        runTest {
            client.on(TdApi.LogOut::class, ok())

            assertEquals(Outcome.Success(Unit), repository().logOut())
        }

    @Test
    fun `connection status follows the client`() =
        runTest {
            TdlibConnectionRepository(client).connectionStatus.test {
                assertEquals(ConnectionStatus.Connecting, awaitItem())

                client.connectionState.value = ConnectionState.WaitingForNetwork

                assertEquals(ConnectionStatus.WaitingForNetwork, awaitItem())
            }
        }
}
