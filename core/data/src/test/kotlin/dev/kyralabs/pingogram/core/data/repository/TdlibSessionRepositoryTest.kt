package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ActiveSession
import dev.kyralabs.pingogram.core.domain.model.ActiveSessions
import dev.kyralabs.pingogram.core.domain.model.SessionDevice
import dev.kyralabs.pingogram.core.domain.model.TwoStepVerificationState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TdlibSessionRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibSessionRepository(client)
    private val twoStepVerification = TdlibTwoStepVerificationRepository(client)

    private fun session(
        id: Long,
        deviceType: TdApi.SessionDeviceType = TdApi.SessionDeviceTypeAndroid(),
        isCurrent: Boolean = false,
        isPasswordPending: Boolean = false,
        isUnconfirmed: Boolean = false,
    ) = TdApi.Session(
        id,
        isCurrent,
        isPasswordPending,
        isUnconfirmed,
        true,
        true,
        deviceType,
        12345,
        "Pingogram",
        "0.3.0",
        false,
        "Example Phone 8",
        "Android",
        "15",
        1_700_000_000,
        1_700_000_600,
        "203.0.113.7",
        "Berlin, Germany",
    )

    private fun answerSessions(vararg sessions: TdApi.Session) =
        client.on(TdApi.GetActiveSessions::class, TdResult.Success(TdApi.Sessions(arrayOf(*sessions), 180)))

    private suspend fun devicesOf(vararg types: TdApi.SessionDeviceType): List<SessionDevice> {
        answerSessions(*types.mapIndexed { index, type -> session(index.toLong(), type) }.toTypedArray())
        return (repository.activeSessions() as Outcome.Success).value.sessions.map { it.device }
    }

    @Test
    fun `maps the sessions in TDLib's order, with the inactive session TTL`() =
        runTest {
            answerSessions(session(1, isCurrent = true), session(2, isUnconfirmed = true, isPasswordPending = true))

            val expected =
                ActiveSessions(
                    sessions =
                        persistentListOf(
                            ActiveSession(
                                id = 1,
                                isCurrent = true,
                                isPasswordPending = false,
                                isUnconfirmed = false,
                                device = SessionDevice.Phone,
                                applicationName = "Pingogram",
                                applicationVersion = "0.3.0",
                                isOfficialApplication = false,
                                deviceModel = "Example Phone 8",
                                platform = "Android",
                                systemVersion = "15",
                                logInDate = 1_700_000_000,
                                lastActiveDate = 1_700_000_600,
                                location = "Berlin, Germany",
                            ),
                            ActiveSession(
                                id = 2,
                                isCurrent = false,
                                isPasswordPending = true,
                                isUnconfirmed = true,
                                device = SessionDevice.Phone,
                                applicationName = "Pingogram",
                                applicationVersion = "0.3.0",
                                isOfficialApplication = false,
                                deviceModel = "Example Phone 8",
                                platform = "Android",
                                systemVersion = "15",
                                logInDate = 1_700_000_000,
                                lastActiveDate = 1_700_000_600,
                                location = "Berlin, Germany",
                            ),
                        ),
                    inactiveSessionTtlDays = 180,
                )
            assertEquals(Outcome.Success(expected), repository.activeSessions())
        }

    @Test
    fun `groups TDLib's device types by kind of device`() =
        runTest {
            val devices =
                devicesOf(
                    TdApi.SessionDeviceTypeAndroid(),
                    TdApi.SessionDeviceTypeIphone(),
                    TdApi.SessionDeviceTypeIpad(),
                    TdApi.SessionDeviceTypeWindows(),
                    TdApi.SessionDeviceTypeMac(),
                    TdApi.SessionDeviceTypeLinux(),
                    TdApi.SessionDeviceTypeUbuntu(),
                    TdApi.SessionDeviceTypeChrome(),
                    TdApi.SessionDeviceTypeFirefox(),
                    TdApi.SessionDeviceTypeEdge(),
                    TdApi.SessionDeviceTypeSafari(),
                    TdApi.SessionDeviceTypeOpera(),
                    TdApi.SessionDeviceTypeBrave(),
                    TdApi.SessionDeviceTypeVivaldi(),
                    TdApi.SessionDeviceTypeXbox(),
                    TdApi.SessionDeviceTypeApple(),
                    TdApi.SessionDeviceTypeUnknown(),
                )

            assertEquals(
                listOf(SessionDevice.Phone, SessionDevice.Phone, SessionDevice.Tablet) +
                    List(4) { SessionDevice.Desktop } +
                    List(7) { SessionDevice.Browser } +
                    listOf(SessionDevice.GameConsole, SessionDevice.Unknown, SessionDevice.Unknown),
                devices,
            )
        }

    @Test
    fun `a session's description stays out of its text`() {
        val text =
            ActiveSession(
                1,
                false,
                false,
                false,
                SessionDevice.Phone,
                "App",
                "1",
                false,
                "Model",
                "OS",
                "1",
                0,
                0,
                "City",
            ).toString()

        assertFalse(listOf("App", "Model", "OS", "City").any { it in text })
    }

    @Test
    fun `loading the sessions maps errors`() =
        runTest {
            client.on(
                TdApi.GetActiveSessions::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 2")),
            )

            assertEquals(Outcome.Failure(DomainError.FloodWait(2)), repository.activeSessions())
        }

    @Test
    fun `loading the sessions without an answer in time fails with a network error`() =
        runTest {
            client.on(TdApi.GetActiveSessions::class) { CompletableDeferred<TdResult<TdApi.Sessions>>().await() }

            val result = async { repository.activeSessions() }
            advanceTimeBy(REQUEST_TIMEOUT)
            runCurrent()

            assertEquals(Outcome.Failure(DomainError.Network), result.await())
        }

    @Test
    fun `terminates a session by its id`() =
        runTest {
            client.on(TdApi.TerminateSession::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.terminate(42))
            assertEquals(42L, client.sentOf<TdApi.TerminateSession>().single().sessionId)
        }

    @Test
    fun `a termination Telegram refuses for a recent login is silent`() =
        runTest {
            client.on(
                TdApi.TerminateSession::class,
                TdResult.Failure(TdError(406, "FRESH_RESET_AUTHORISATION_FORBIDDEN")),
            )

            assertEquals(Outcome.Failure(DomainError.Silent), repository.terminate(42))
        }

    @Test
    fun `terminates all other sessions`() =
        runTest {
            client.on(TdApi.TerminateAllOtherSessions::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.terminateAllOthers())
            assertEquals(1, client.sentOf<TdApi.TerminateAllOtherSessions>().size)
        }

    @Test
    fun `confirms a session by its id`() =
        runTest {
            client.on(TdApi.ConfirmSession::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.confirm(9))
            assertEquals(9L, client.sentOf<TdApi.ConfirmSession>().single().sessionId)
        }

    // 2-step verification

    private fun passwordState(
        hasPassword: Boolean = true,
        hasRecoveryEmail: Boolean = false,
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

    @Test
    fun `reports whether a password and a recovery email are set`() =
        runTest {
            client.on(TdApi.GetPasswordState::class, TdResult.Success(passwordState(hasRecoveryEmail = true)))

            assertEquals(
                Outcome.Success(TwoStepVerificationState(hasPassword = true, hasRecoveryEmail = true)),
                twoStepVerification.state(),
            )
        }

    @Test
    fun `reports a recovery email waiting for confirmation and a pending reset`() =
        runTest {
            client.on(
                TdApi.GetPasswordState::class,
                TdResult.Success(passwordState(pendingEmail = "a***@g***.com", pendingResetDate = 1_700_000_000)),
            )

            assertEquals(
                Outcome.Success(
                    TwoStepVerificationState(
                        hasPassword = true,
                        hasRecoveryEmail = false,
                        pendingRecoveryEmailPattern = "a***@g***.com",
                        pendingResetDate = 1_700_000_000,
                    ),
                ),
                twoStepVerification.state(),
            )
        }

    @Test
    fun `no password is reported as such`() =
        runTest {
            client.on(TdApi.GetPasswordState::class, TdResult.Success(passwordState(hasPassword = false)))

            assertEquals(
                Outcome.Success(TwoStepVerificationState(hasPassword = false, hasRecoveryEmail = false)),
                twoStepVerification.state(),
            )
        }

    @Test
    fun `the email address pattern stays out of the state's text`() {
        val text = TwoStepVerificationState(true, false, pendingRecoveryEmailPattern = "a***@g***.com").toString()

        assertFalse("a***@g***.com" in text)
    }

    @Test
    fun `the password state maps errors`() =
        runTest {
            client.on(TdApi.GetPasswordState::class, TdResult.Failure(TdError(500, "Network is unreachable")))

            assertEquals(Outcome.Failure(DomainError.Network), twoStepVerification.state())
        }

    @Test
    fun `confirming a QR code login sends the link and returns the new session`() =
        runTest {
            client.on(
                TdApi.ConfirmQrCodeAuthentication::class,
                TdResult.Success(session(7, TdApi.SessionDeviceTypeMac())),
            )

            val outcome = repository.confirmQrCodeLogin("tg://login?token=abc")

            assertEquals("tg://login?token=abc", client.sentOf<TdApi.ConfirmQrCodeAuthentication>().single().link)
            val confirmed = (outcome as Outcome.Success).value
            assertEquals(7L, confirmed.id)
            assertEquals(SessionDevice.Desktop, confirmed.device)
        }

    @Test
    fun `an expired QR code login says so`() =
        runTest {
            client.on(TdApi.ConfirmQrCodeAuthentication::class, TdResult.Failure(TdError(400, "AUTH_TOKEN_EXPIRED")))

            assertEquals(
                Outcome.Failure(DomainError.QrLoginExpired),
                repository.confirmQrCodeLogin("tg://login?token=x"),
            )
        }
}
