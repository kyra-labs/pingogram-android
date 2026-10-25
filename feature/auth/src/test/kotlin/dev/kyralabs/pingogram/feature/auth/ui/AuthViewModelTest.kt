package dev.kyralabs.pingogram.feature.auth.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibAuthRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibConnectionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibCountryRepository
import dev.kyralabs.pingogram.core.domain.model.AuthCodeType
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibServers
import dev.kyralabs.pingogram.core.testing.FakeDeviceAccountsRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.phone.matching
import dev.kyralabs.pingogram.feature.auth.ui.AuthTestFixtures.UK_CALLING_CODE
import dev.kyralabs.pingogram.feature.auth.ui.AuthTestFixtures.UK_INTERNATIONAL
import dev.kyralabs.pingogram.feature.auth.ui.AuthTestFixtures.UK_NUMBER
import dev.kyralabs.pingogram.feature.auth.ui.AuthTestFixtures.answerCountryRequests
import dev.kyralabs.pingogram.feature.auth.ui.AuthTestFixtures.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val client = FakeTdlibClient(authState = AuthState.WaitPhoneNumber, connectionState = ConnectionState.Ready)
    private val savedState = SavedStateHandle()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        client.answerCountryRequests()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.authRepository() =
        TdlibAuthRepository(client, StandardTestDispatcher(testScheduler), testScheduler.timeSource)

    // Through the real TDLib repositories on FakeTdlibClient, as the app wires them.
    private fun TestScope.viewModel(
        handle: SavedStateHandle = savedState,
        authRepository: TdlibAuthRepository = authRepository(),
        deviceAccounts: FakeDeviceAccountsRepository = FakeDeviceAccountsRepository(),
    ): AuthViewModel =
        AuthViewModel(
            savedStateHandle = handle,
            authRepository = authRepository,
            countryRepository = TdlibCountryRepository(client),
            connectionRepository = TdlibConnectionRepository(client),
            deviceAccounts = deviceAccounts,
            timeSource = testScheduler.timeSource,
        ).also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: AuthViewModel): List<AuthEffect> {
        val effects = mutableListOf<AuthEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: AuthViewModel,
        vararg events: AuthEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    private suspend fun TestScope.tdlib(state: TdApi.AuthorizationState) {
        client.emitAuthorizationState(state)
        runCurrent()
    }

    private fun TestScope.advanceSeconds(seconds: Int) {
        advanceTimeBy(seconds.seconds)
        runCurrent()
    }

    private val AuthViewModel.state get() = uiState.value

    private inline fun <reified T : AuthStepUiState> AuthViewModel.step(): T =
        assertInstanceOf(T::class.java, uiState.value.step)

    /** A view model on phone number entry with the UK number typed. */
    private fun TestScope.onPhoneNumberEntry(): AuthViewModel =
        viewModel().also { send(it, AuthEvent.StartMessaging, AuthEvent.PhoneNumberChanged(UK_NUMBER)) }

    /** The SMS code step for [phoneNumber], as TDLib reports it after `setAuthenticationPhoneNumber`. */
    private fun waitCodeFor(phoneNumber: String) =
        TdApiFixtures.waitCode(
            TdApiFixtures.authenticationCodeInfo(
                phoneNumber = phoneNumber,
                type = TdApi.AuthenticationCodeTypeSms(5),
                nextType = TdApi.AuthenticationCodeTypeCall(5),
                timeoutSeconds = 60,
            ),
        )

    private fun SavedStateHandle.values(): List<Any?> = keys().map { get<Any>(it) }

    private fun failure(
        message: String,
        code: Int = 400,
    ) = TdResult.Failure(TdError(code, message))

    @Nested
    inner class Start {
        @Test
        fun `shows loading until TDLib reports its first step`() =
            runTest {
                client.authState.value = AuthState.Initializing

                val viewModel = viewModel()

                assertEquals(AuthStepUiState.Loading, viewModel.state.step)
                assertFalse(viewModel.state.canGoBack)
            }

        @Test
        fun `waiting for a phone number shows the welcome screen first`() =
            runTest {
                val viewModel = viewModel()

                assertEquals(AuthStepUiState.Welcome, viewModel.state.step)
                assertFalse(viewModel.state.canGoBack)
            }

        @Test
        fun `start messaging shows phone number entry with the country of the IP address`() =
            runTest {
                val viewModel = viewModel()

                send(viewModel, AuthEvent.StartMessaging)

                val phone = viewModel.step<AuthStepUiState.PhoneNumber>()
                assertEquals(UK_CALLING_CODE, phone.callingCode)
                assertEquals("GB", phone.country?.isoCode)
                assertFalse(phone.isChangingNumber)
                assertTrue(viewModel.state.canGoBack)
            }

        @Test
        fun `back from phone number entry returns to the welcome screen`() =
            runTest {
                val viewModel = viewModel()
                send(viewModel, AuthEvent.StartMessaging)

                send(viewModel, AuthEvent.Back)

                assertEquals(AuthStepUiState.Welcome, viewModel.state.step)
            }

        @Test
        fun `an account being added starts at phone number entry, and back leaves its sign-in`() =
            runTest {
                val accounts = FakeDeviceAccountsRepository().apply { adding.value = true }
                val viewModel = viewModel(deviceAccounts = accounts)

                assertFalse(viewModel.step<AuthStepUiState.PhoneNumber>().isChangingNumber)
                assertTrue(viewModel.state.canGoBack)
                // Back leaves the flow, so the gesture shows no step of it coming in.
                assertNull(viewModel.state.backStep)

                send(viewModel, AuthEvent.Back)

                assertEquals(1, accounts.cancels)
            }

        @Test
        fun `without an account being added, back from the welcome screen leaves nothing`() =
            runTest {
                val accounts = FakeDeviceAccountsRepository()
                val viewModel = viewModel(deviceAccounts = accounts)

                send(viewModel, AuthEvent.Back)

                assertEquals(AuthStepUiState.Welcome, viewModel.state.step)
                assertEquals(0, accounts.cancels)
            }

        @Test
        fun `the terms of service and privacy policy open Telegram's pages`() =
            runTest {
                val viewModel = viewModel()
                val effects = effectsOf(viewModel)

                send(viewModel, AuthEvent.OpenTermsOfService, AuthEvent.OpenPrivacyPolicy)

                assertEquals(
                    listOf(
                        AuthEffect.OpenUrl(AuthViewModel.TERMS_OF_SERVICE_URL),
                        AuthEffect.OpenUrl(AuthViewModel.PRIVACY_POLICY_URL),
                    ),
                    effects,
                )
            }

        @Test
        fun `the connection status is part of the state`() =
            runTest {
                val viewModel = viewModel()

                client.connectionState.value = ConnectionState.WaitingForNetwork
                runCurrent()

                assertEquals(ConnectionStatus.WaitingForNetwork, viewModel.state.connection)
                assertTrue(viewModel.state.isOffline)
            }
    }

    @Nested
    inner class PhoneNumber {
        @Test
        fun `typing a number formats it and detects the country`() =
            runTest {
                val viewModel = viewModel()
                send(
                    viewModel,
                    AuthEvent.StartMessaging,
                    AuthEvent.CallingCodeChanged("1"),
                    AuthEvent.PhoneNumberChanged("416 555-01"),
                )

                val phone = viewModel.step<AuthStepUiState.PhoneNumber>()
                assertEquals("41655501", phone.number)
                assertEquals("--- --- ----", phone.numberFormat)
                assertEquals("CA", phone.country?.isoCode)
            }

        @Test
        fun `a picked country stays while the number is empty`() =
            runTest {
                val viewModel = viewModel()

                // TDLib reports +1 without more digits as the United States.
                send(viewModel, AuthEvent.StartMessaging, AuthEvent.OpenCountryPicker, AuthEvent.CountrySelected("CA"))

                val phone = viewModel.step<AuthStepUiState.PhoneNumber>()
                assertEquals("1", phone.callingCode)
                assertEquals("CA", phone.country?.isoCode)
                assertNull(phone.countryPicker)
            }

        @Test
        fun `the country picker lists visible countries matching the query`() =
            runTest {
                val viewModel = viewModel()
                send(viewModel, AuthEvent.StartMessaging, AuthEvent.OpenCountryPicker)

                fun visible() =
                    viewModel
                        .step<AuthStepUiState.PhoneNumber>()
                        .countryPicker!!
                        .countries
                        .map { it.isoCode }

                val all = visible()
                send(viewModel, AuthEvent.CountryQueryChanged("germ"))
                val byName = visible()
                send(viewModel, AuthEvent.CountryQueryChanged("+44"))
                val byCallingCode = visible()
                send(viewModel, AuthEvent.CountryQueryChanged("us"))
                val byIsoCode = visible()

                assertEquals(listOf("CA", "DE", "GB", "US"), all)
                assertEquals(listOf("DE"), byName)
                assertEquals(listOf("GB"), byCallingCode)
                assertEquals(listOf("US"), byIsoCode)
            }

        @Test
        fun `the country picker marks the country of the number`() =
            runTest {
                val viewModel = viewModel()
                send(viewModel, AuthEvent.StartMessaging, AuthEvent.OpenCountryPicker)

                assertEquals("GB", viewModel.step<AuthStepUiState.PhoneNumber>().countryPicker?.selectedIsoCode)
            }

        @Test
        fun `closing the country picker keeps the country`() =
            runTest {
                val viewModel = viewModel()
                send(viewModel, AuthEvent.StartMessaging, AuthEvent.OpenCountryPicker)
                assertTrue(viewModel.state.canGoBack)

                send(viewModel, AuthEvent.Back)

                val phone = viewModel.step<AuthStepUiState.PhoneNumber>()
                assertNull(phone.countryPicker)
                assertEquals("GB", phone.country?.isoCode)
            }

        @Test
        fun `a pasted international number is split into calling code and number`() =
            runTest {
                val viewModel = viewModel()
                send(viewModel, AuthEvent.StartMessaging, AuthEvent.CallingCodeChanged(""))

                send(viewModel, AuthEvent.PhoneNumberChanged("+49 1512 3456789"))

                val phone = viewModel.step<AuthStepUiState.PhoneNumber>()
                assertEquals("49" to "15123456789", phone.callingCode to phone.number)
                assertEquals("DE", phone.country?.isoCode)
            }

        @Test
        fun `a test account's number pasted as one splits after +999`() =
            runTest {
                val viewModel = viewModel()
                send(viewModel, AuthEvent.StartMessaging, AuthEvent.CallingCodeChanged(""))

                send(viewModel, AuthEvent.PhoneNumberChanged("+999 66 2 1234"))

                val phone = viewModel.step<AuthStepUiState.PhoneNumber>()
                assertEquals("999" to "6621234", phone.callingCode to phone.number)
            }

        // REQUIREMENTS.md FR-A9: TDLib restarts on the test servers before it gets the number.
        @Test
        fun `a test account's number keeps phone entry, the number and progress while TDLib restarts`() =
            runTest {
                val answer = CompletableDeferred<TdResult<TdApi.Ok>>()
                client.on(TdApi.SetAuthenticationPhoneNumber::class) { answer.await() }
                val viewModel = viewModel()
                send(viewModel, AuthEvent.StartMessaging, AuthEvent.PhoneNumberChanged("+9996621234"))

                send(viewModel, AuthEvent.SubmitPhoneNumber)
                val restart =
                    listOf(
                        TdApiFixtures.closing(),
                        TdApiFixtures.closed(),
                        TdApiFixtures.waitTdlibParameters(),
                        TdApiFixtures.waitPhoneNumber(),
                    ).map {
                        tdlib(it)
                        viewModel.state
                    }
                answer.complete(ok())
                runCurrent()
                val answered = viewModel.state
                tdlib(waitCodeFor("+9996621234"))

                assertEquals(listOf(TdlibServers.Test), client.serverSwitches)
                restart.forEach { state ->
                    val phone = assertInstanceOf(AuthStepUiState.PhoneNumber::class.java, state.step)
                    assertEquals("999" to "6621234", phone.callingCode to phone.number)
                    assertEquals(AuthAction.SubmitPhoneNumber, state.pendingAction)
                }
                assertEquals(AuthAction.SubmitPhoneNumber, answered.pendingAction)
                assertNull(viewModel.state.pendingAction)
                assertInstanceOf(AuthStepUiState.Code::class.java, viewModel.state.step)
            }

        @Test
        fun `a restart that is no server switch still clears the login`() =
            runTest {
                val viewModel = onPhoneNumberEntry()

                tdlib(TdApiFixtures.closed())
                tdlib(TdApiFixtures.waitPhoneNumber())

                assertEquals(AuthStepUiState.Welcome, viewModel.state.step)
            }

        @Test
        fun `a number too short for E164 is rejected without asking TDLib`() =
            runTest {
                val viewModel = viewModel()
                send(viewModel, AuthEvent.StartMessaging, AuthEvent.PhoneNumberChanged("791"))

                send(viewModel, AuthEvent.SubmitPhoneNumber)

                assertEquals(DomainError.PhoneNumberInvalid, viewModel.step<AuthStepUiState.PhoneNumber>().error)
                assertTrue(client.sentOf<TdApi.SetAuthenticationPhoneNumber>().isEmpty())
            }

        @Test
        fun `numbers are capped at 15 digits`() =
            runTest {
                val viewModel = viewModel()

                send(viewModel, AuthEvent.StartMessaging, AuthEvent.PhoneNumberChanged("1234567890123456789"))

                assertEquals(13, viewModel.step<AuthStepUiState.PhoneNumber>().number.length)
            }

        @Test
        fun `submitting sends the international number and shows progress until TDLib answers`() =
            runTest {
                val answer = CompletableDeferred<TdResult<TdApi.Ok>>()
                client.on(TdApi.SetAuthenticationPhoneNumber::class) { answer.await() }
                val viewModel = onPhoneNumberEntry()

                send(viewModel, AuthEvent.SubmitPhoneNumber)
                val whileSending = viewModel.state
                tdlib(waitCodeFor(UK_INTERNATIONAL))
                answer.complete(ok())
                runCurrent()

                assertEquals(UK_INTERNATIONAL, client.sentOf<TdApi.SetAuthenticationPhoneNumber>().single().phoneNumber)
                assertEquals(AuthAction.SubmitPhoneNumber, whileSending.pendingAction)
                assertFalse(whileSending.canSubmit)
                assertNull(viewModel.state.pendingAction)
                assertInstanceOf(AuthStepUiState.Code::class.java, viewModel.state.step)
            }

        // TDLib's answer and the step it leads to come on separate paths, in either order.
        @Test
        fun `when TDLib answers before reporting the code step, progress stays and nothing is sent twice`() =
            runTest {
                client.on(TdApi.SetAuthenticationPhoneNumber::class, ok())
                val viewModel = onPhoneNumberEntry()

                send(viewModel, AuthEvent.SubmitPhoneNumber)
                val answered = viewModel.state
                send(viewModel, AuthEvent.SubmitPhoneNumber)
                tdlib(waitCodeFor(UK_INTERNATIONAL))

                assertEquals(AuthAction.SubmitPhoneNumber, answered.pendingAction)
                assertInstanceOf(AuthStepUiState.PhoneNumber::class.java, answered.step)
                assertEquals(1, client.sentOf<TdApi.SetAuthenticationPhoneNumber>().size)
                assertNull(viewModel.state.pendingAction)
                assertInstanceOf(AuthStepUiState.Code::class.java, viewModel.state.step)
            }

        @Test
        fun `when TDLib reports the code step before answering, progress ends with the answer`() =
            runTest {
                client.on(TdApi.SetAuthenticationPhoneNumber::class) {
                    client.emitAuthorizationState(waitCodeFor(UK_INTERNATIONAL))
                    ok()
                }
                val viewModel = onPhoneNumberEntry()

                send(viewModel, AuthEvent.SubmitPhoneNumber)

                assertNull(viewModel.state.pendingAction)
                assertInstanceOf(AuthStepUiState.Code::class.java, viewModel.state.step)
            }

        @Test
        fun `only one request is sent at a time`() =
            runTest {
                val answer = CompletableDeferred<TdResult<TdApi.Ok>>()
                client.on(TdApi.SetAuthenticationPhoneNumber::class) { answer.await() }
                val viewModel = onPhoneNumberEntry()

                send(viewModel, AuthEvent.SubmitPhoneNumber, AuthEvent.SubmitPhoneNumber, AuthEvent.LogInWithQrCode)

                assertEquals(
                    1,
                    client.sent.count {
                        it is TdApi.SetAuthenticationPhoneNumber ||
                            it is TdApi.RequestQrCodeAuthentication
                    },
                )
            }

        @Test
        fun `a number rejected by Telegram shows an inline error, cleared by typing`() =
            runTest {
                client.on(TdApi.SetAuthenticationPhoneNumber::class, failure("PHONE_NUMBER_INVALID"))
                val viewModel = onPhoneNumberEntry()
                val effects = effectsOf(viewModel)

                send(viewModel, AuthEvent.SubmitPhoneNumber)
                val error = viewModel.step<AuthStepUiState.PhoneNumber>().error
                send(viewModel, AuthEvent.PhoneNumberChanged("79111234567"))

                assertEquals(DomainError.PhoneNumberInvalid, error)
                assertNull(viewModel.step<AuthStepUiState.PhoneNumber>().error)
                assertTrue(effects.isEmpty())
            }

        @Test
        fun `a banned number shows an inline error`() =
            runTest {
                client.on(TdApi.SetAuthenticationPhoneNumber::class, failure("PHONE_NUMBER_BANNED"))
                val viewModel = onPhoneNumberEntry()

                send(viewModel, AuthEvent.SubmitPhoneNumber)

                assertEquals(DomainError.PhoneNumberBanned, viewModel.step<AuthStepUiState.PhoneNumber>().error)
            }
    }

    @Nested
    inner class Errors {
        @Test
        fun `a flood wait disables submitting and counts down to zero`() =
            runTest {
                client.on(
                    TdApi.SetAuthenticationPhoneNumber::class,
                    failure("Too Many Requests: retry after 3", code = 429),
                )
                val viewModel = onPhoneNumberEntry()
                val effects = effectsOf(viewModel)

                send(viewModel, AuthEvent.SubmitPhoneNumber)
                val countdown = mutableListOf(viewModel.state.floodWaitSeconds)
                send(viewModel, AuthEvent.SubmitPhoneNumber)
                advanceSeconds(1)
                val total = viewModel.state.floodWaitTotalSeconds
                countdown += viewModel.state.floodWaitSeconds
                repeat(2) {
                    advanceSeconds(1)
                    countdown += viewModel.state.floodWaitSeconds
                }

                assertEquals(listOf(3L, 2L, 1L, null), countdown)
                assertEquals(3L, total)
                assertEquals(1, client.sentOf<TdApi.SetAuthenticationPhoneNumber>().size)
                assertTrue(viewModel.state.canSubmit)
                assertTrue(effects.isEmpty())
            }

        @Test
        fun `after a flood wait the request can be sent again`() =
            runTest {
                client.on(TdApi.SetAuthenticationPhoneNumber::class, failure("FLOOD_WAIT_2", code = 420))
                val viewModel = onPhoneNumberEntry()
                send(viewModel, AuthEvent.SubmitPhoneNumber)
                client.on(TdApi.SetAuthenticationPhoneNumber::class, ok())

                advanceSeconds(2)
                send(viewModel, AuthEvent.SubmitPhoneNumber)

                assertEquals(2, client.sentOf<TdApi.SetAuthenticationPhoneNumber>().size)
            }

        @Test
        fun `no answer in time shows a network error`() =
            runTest {
                val never = CompletableDeferred<TdResult<TdApi.Ok>>()
                client.on(TdApi.SetAuthenticationPhoneNumber::class) { never.await() }
                val viewModel = onPhoneNumberEntry()
                val effects = effectsOf(viewModel)

                send(viewModel, AuthEvent.SubmitPhoneNumber)
                advanceSeconds(89)
                val stillWaiting = viewModel.state.pendingAction
                advanceSeconds(1)

                assertEquals(AuthAction.SubmitPhoneNumber, stillWaiting)
                assertEquals(listOf(AuthEffect.ShowError(DomainError.Network)), effects)
                assertNull(viewModel.state.pendingAction)
            }

        @Test
        fun `an answer within TDLib's own 60 seconds is awaited`() =
            runTest {
                client.on(TdApi.SetAuthenticationPhoneNumber::class) {
                    delay(60.seconds)
                    failure("PHONE_NUMBER_INVALID")
                }
                val viewModel = onPhoneNumberEntry()
                val effects = effectsOf(viewModel)

                send(viewModel, AuthEvent.SubmitPhoneNumber)
                advanceSeconds(60)

                assertTrue(effects.isEmpty())
                assertEquals(DomainError.PhoneNumberInvalid, viewModel.step<AuthStepUiState.PhoneNumber>().error)
            }

        @Test
        fun `an unauthorized error goes to a snackbar`() =
            runTest {
                client.on(TdApi.SetAuthenticationPhoneNumber::class, failure("Unauthorized", code = 401))
                val viewModel = onPhoneNumberEntry()
                val effects = effectsOf(viewModel)

                send(viewModel, AuthEvent.SubmitPhoneNumber)

                assertEquals(listOf(AuthEffect.ShowError(DomainError.Unauthorized)), effects)
                assertNull(viewModel.step<AuthStepUiState.PhoneNumber>().error)
            }

        @Test
        fun `an unknown error goes to a snackbar with TDLib's text for debug builds`() =
            runTest {
                client.on(TdApi.SetAuthenticationPhoneNumber::class, failure("SOMETHING_NEW", code = 500))
                val viewModel = onPhoneNumberEntry()
                val effects = effectsOf(viewModel)

                send(viewModel, AuthEvent.SubmitPhoneNumber)

                assertEquals(listOf(AuthEffect.ShowError(DomainError.Unknown(500, "SOMETHING_NEW"))), effects)
            }

        @Test
        fun `a 406 asks to check the field being submitted, without TDLib's text`() =
            runTest {
                // A number one digit short passes the local length check; Telegram answers 406.
                client.on(TdApi.SetAuthenticationPhoneNumber::class, failure("PHONE_NUMBER_INVALID", code = 406))
                val viewModel = onPhoneNumberEntry()
                val effects = effectsOf(viewModel)

                send(viewModel, AuthEvent.SubmitPhoneNumber)

                assertTrue(effects.isEmpty())
                assertEquals(DomainError.InputRejected, viewModel.step<AuthStepUiState.PhoneNumber>().error)
                assertNull(viewModel.state.pendingAction)
            }

        @Test
        fun `a 406 for a request without a field shows the same neutral text in a snackbar`() =
            runTest {
                client.on(TdApi.RequestQrCodeAuthentication::class, failure("UPDATE_APP_TO_LOGIN", code = 406))
                val viewModel = onPhoneNumberEntry()
                val effects = effectsOf(viewModel)

                send(viewModel, AuthEvent.LogInWithQrCode)

                assertEquals(listOf(AuthEffect.ShowError(DomainError.InputRejected)), effects)
            }

        @Test
        fun `TDLib's own request for an app update is reported as such`() =
            runTest {
                client.authState.value = AuthState.from(TdApiFixtures.waitPassword())!!
                client.on(TdApi.CheckAuthenticationPassword::class, failure("Application update is needed to log in"))
                val viewModel = viewModel()
                val effects = effectsOf(viewModel)

                send(viewModel, AuthEvent.PasswordChanged("secret"), AuthEvent.SubmitPassword)

                assertEquals(listOf(AuthEffect.ShowError(DomainError.AppUpdateRequired)), effects)
            }
    }

    @Nested
    inner class Code {
        private val smsThenCall =
            TdApiFixtures.waitCode(
                TdApiFixtures.authenticationCodeInfo(
                    phoneNumber = UK_INTERNATIONAL,
                    type = TdApi.AuthenticationCodeTypeSms(5),
                    nextType = TdApi.AuthenticationCodeTypeCall(5),
                    timeoutSeconds = 3,
                ),
            )

        private suspend fun TestScope.onCode(state: TdApi.AuthorizationStateWaitCode = smsThenCall): AuthViewModel {
            client.authState.value = AuthState.from(state)!!
            return viewModel()
        }

        @Test
        fun `shows the code type, its length and the resend countdown`() =
            runTest {
                val viewModel = onCode()

                val code = viewModel.step<AuthStepUiState.Code>()
                val countdown = mutableListOf(code.resendInSeconds)
                val totals = mutableListOf(code.resendTotalSeconds)
                repeat(3) {
                    advanceSeconds(1)
                    countdown += viewModel.step<AuthStepUiState.Code>().resendInSeconds
                    totals += viewModel.step<AuthStepUiState.Code>().resendTotalSeconds
                }

                assertEquals(AuthCodeType.Sms(5), code.type)
                assertEquals(AuthCodeType.Call(5), code.nextType)
                assertEquals(5, code.codeLength)
                assertEquals(listOf(3L, 2L, 1L, 0L), countdown)
                assertEquals(listOf(3L, 3L, 3L, 3L), totals)
                assertTrue(viewModel.step<AuthStepUiState.Code>().canResend)
            }

        @Test
        fun `shows the number formatted by TDLib`() =
            runTest {
                val viewModel = onCode()

                assertEquals("+44 7911 123456", viewModel.step<AuthStepUiState.Code>().phoneNumber)
            }

        @Test
        fun `a complete code submits itself, from pasted text too`() =
            runTest {
                client.on(TdApi.CheckAuthenticationCode::class, ok())
                val viewModel = onCode()

                send(viewModel, AuthEvent.CodeChanged("Login code: 12345. Do not share it."))

                assertEquals("12345", client.sentOf<TdApi.CheckAuthenticationCode>().single().code)
            }

        @Test
        fun `typing past a complete code does not send it again`() =
            runTest {
                client.on(TdApi.CheckAuthenticationCode::class, failure("PHONE_CODE_INVALID"))
                val viewModel = onCode()

                send(viewModel, AuthEvent.CodeChanged("12345"), AuthEvent.CodeChanged("123456"))

                assertEquals("12345", viewModel.step<AuthStepUiState.Code>().code)
                assertEquals(1, client.sentOf<TdApi.CheckAuthenticationCode>().size)
            }

        @Test
        fun `a code completed again after an edit is sent again`() =
            runTest {
                client.on(TdApi.CheckAuthenticationCode::class, failure("PHONE_CODE_INVALID"))
                val viewModel = onCode()

                send(
                    viewModel,
                    AuthEvent.CodeChanged("12345"),
                    AuthEvent.CodeChanged("1234"),
                    AuthEvent.CodeChanged("12346"),
                )

                assertEquals(listOf("12345", "12346"), client.sentOf<TdApi.CheckAuthenticationCode>().map { it.code })
            }

        @Test
        fun `an incomplete code is not submitted`() =
            runTest {
                val viewModel = onCode()

                send(viewModel, AuthEvent.CodeChanged("123"))

                assertEquals("123", viewModel.step<AuthStepUiState.Code>().code)
                assertTrue(client.sentOf<TdApi.CheckAuthenticationCode>().isEmpty())
            }

        @Test
        fun `a word code is kept as typed and submitted on request`() =
            runTest {
                client.on(TdApi.CheckAuthenticationCode::class, ok())
                val viewModel =
                    onCode(
                        TdApiFixtures.waitCode(
                            TdApiFixtures.authenticationCodeInfo(type = TdApi.AuthenticationCodeTypeSmsWord("a")),
                        ),
                    )

                send(viewModel, AuthEvent.CodeChanged("apple"))
                val beforeSubmit = client.sentOf<TdApi.CheckAuthenticationCode>().size
                send(viewModel, AuthEvent.SubmitCode)

                assertNull(viewModel.step<AuthStepUiState.Code>().codeLength)
                assertEquals(0, beforeSubmit)
                assertEquals("apple", client.sentOf<TdApi.CheckAuthenticationCode>().single().code)
            }

        @Test
        fun `a wrong code shows an inline error, cleared by typing`() =
            runTest {
                client.on(TdApi.CheckAuthenticationCode::class, failure("PHONE_CODE_INVALID"))
                val viewModel = onCode()

                send(viewModel, AuthEvent.CodeChanged("12345"))
                val error = viewModel.step<AuthStepUiState.Code>().error
                send(viewModel, AuthEvent.CodeChanged("1234"))

                assertEquals(DomainError.CodeInvalid, error)
                assertNull(viewModel.step<AuthStepUiState.Code>().error)
            }

        @Test
        fun `an expired code shows an inline error`() =
            runTest {
                client.on(TdApi.CheckAuthenticationCode::class, failure("PHONE_CODE_EXPIRED"))
                val viewModel = onCode()

                send(viewModel, AuthEvent.CodeChanged("12345"))

                assertEquals(DomainError.CodeExpired, viewModel.step<AuthStepUiState.Code>().error)
            }

        @Test
        fun `a new code can be requested only after the countdown`() =
            runTest {
                client.on(TdApi.ResendAuthenticationCode::class, ok())
                val viewModel = onCode()

                send(viewModel, AuthEvent.ResendCode)
                val early = client.sentOf<TdApi.ResendAuthenticationCode>().size
                advanceSeconds(3)
                send(viewModel, AuthEvent.CodeChanged("12"), AuthEvent.ResendCode)
                tdlib(waitCodeFor(UK_INTERNATIONAL))

                val reason = client.sentOf<TdApi.ResendAuthenticationCode>().single().reason
                assertEquals(0, early)
                assertTrue(reason is TdApi.ResendCodeReasonUserRequest)
                assertEquals("", viewModel.step<AuthStepUiState.Code>().code)
            }

        @Test
        fun `a resent code restarts the countdown with the next type`() =
            runTest {
                val viewModel = onCode()
                advanceSeconds(3)

                tdlib(
                    TdApiFixtures.waitCode(
                        TdApiFixtures.authenticationCodeInfo(
                            type = TdApi.AuthenticationCodeTypeCall(5),
                            nextType = null,
                            timeoutSeconds = 60,
                        ),
                    ),
                )

                val code = viewModel.step<AuthStepUiState.Code>()
                assertEquals(AuthCodeType.Call(5), code.type)
                assertEquals(60L, code.resendInSeconds)
                assertEquals(60L, code.resendTotalSeconds)
                assertFalse(code.canResend)
            }

        @Test
        fun `without a next type a new code cannot be requested`() =
            runTest {
                val viewModel =
                    onCode(
                        TdApiFixtures.waitCode(
                            TdApiFixtures.authenticationCodeInfo(nextType = null, timeoutSeconds = 0),
                        ),
                    )

                send(viewModel, AuthEvent.ResendCode)

                assertFalse(viewModel.step<AuthStepUiState.Code>().canResend)
                assertTrue(client.sentOf<TdApi.ResendAuthenticationCode>().isEmpty())
            }

        @Test
        fun `changing the number returns to phone entry, and back returns to the code`() =
            runTest {
                val viewModel = onPhoneNumberEntry()
                tdlib(smsThenCall)

                send(viewModel, AuthEvent.ChangePhoneNumber)
                val phone = viewModel.step<AuthStepUiState.PhoneNumber>()
                send(viewModel, AuthEvent.Back)

                assertTrue(phone.isChangingNumber)
                assertEquals(UK_NUMBER, phone.number)
                assertInstanceOf(AuthStepUiState.Code::class.java, viewModel.state.step)
            }

        @Test
        fun `back from the code returns to phone entry`() =
            runTest {
                val viewModel = onCode()

                send(viewModel, AuthEvent.Back)

                assertTrue(viewModel.step<AuthStepUiState.PhoneNumber>().isChangingNumber)
            }

        @Test
        fun `a new number is sent from the code step and leads to its code`() =
            runTest {
                client.on(TdApi.SetAuthenticationPhoneNumber::class, ok())
                val viewModel = onCode()

                send(
                    viewModel,
                    AuthEvent.ChangePhoneNumber,
                    AuthEvent.CallingCodeChanged("49"),
                    AuthEvent.PhoneNumberChanged("15123456789"),
                    AuthEvent.SubmitPhoneNumber,
                )
                // Answered, but TDLib has not reported the new code yet: the old code must not come back.
                val answered = viewModel.step<AuthStepUiState.PhoneNumber>()
                tdlib(waitCodeFor("+4915123456789"))

                assertEquals("+4915123456789", client.sentOf<TdApi.SetAuthenticationPhoneNumber>().single().phoneNumber)
                assertTrue(answered.isChangingNumber)
                assertEquals("+49 1512 3456789", viewModel.step<AuthStepUiState.Code>().phoneNumber)
            }

        @Test
        fun `a rejected code is not submitted again by itself`() =
            runTest {
                client.on(TdApi.CheckAuthenticationCode::class, failure("PHONE_CODE_INVALID"))
                val viewModel = onCode()

                send(viewModel, AuthEvent.CodeChanged("12345"))
                // A sixth digit is dropped, which leaves the rejected code.
                send(viewModel, AuthEvent.CodeChanged("123456"))
                val afterExtraDigit = viewModel.step<AuthStepUiState.Code>()
                send(viewModel, AuthEvent.CodeChanged("1234"), AuthEvent.CodeChanged("12345"))
                val attemptsWithRejectedCode = client.sentOf<TdApi.CheckAuthenticationCode>().size
                send(viewModel, AuthEvent.CodeChanged("1234"), AuthEvent.CodeChanged("12346"))

                assertEquals(DomainError.CodeInvalid, afterExtraDigit.error)
                assertEquals(1, attemptsWithRejectedCode)
                assertEquals(listOf("12345", "12346"), client.sentOf<TdApi.CheckAuthenticationCode>().map { it.code })
            }

        @Test
        fun `a code answered with 406 asks to check it and is not submitted again by itself`() =
            runTest {
                client.on(TdApi.CheckAuthenticationCode::class, failure("PHONE_CODE_INVALID", code = 406))
                val viewModel = onCode()

                send(viewModel, AuthEvent.CodeChanged("12345"), AuthEvent.CodeChanged("123456"))

                assertEquals(DomainError.InputRejected, viewModel.step<AuthStepUiState.Code>().error)
                assertEquals(1, client.sentOf<TdApi.CheckAuthenticationCode>().size)
            }

        @Test
        fun `a screen created later counts down from when the code was sent`() =
            runTest {
                client.authState.value =
                    AuthState.from(TdApiFixtures.waitCode(TdApiFixtures.authenticationCodeInfo(timeoutSeconds = 60)))!!
                val repository = authRepository()
                viewModel(authRepository = repository)
                advanceSeconds(20)

                val recreated = viewModel(handle = SavedStateHandle(), authRepository = repository)

                assertEquals(40L, recreated.step<AuthStepUiState.Code>().resendInSeconds)
            }

        @Test
        fun `a fragment code opens fragment com`() =
            runTest {
                val url = "https://fragment.com/login"
                val viewModel =
                    onCode(
                        TdApiFixtures.waitCode(
                            TdApiFixtures.authenticationCodeInfo(type = TdApi.AuthenticationCodeTypeFragment(url, 5)),
                        ),
                    )
                val effects = effectsOf(viewModel)

                send(viewModel, AuthEvent.OpenFragment)

                assertEquals(listOf(AuthEffect.OpenUrl(url)), effects)
            }
    }

    @Nested
    inner class QrCode {
        @Test
        fun `QR login shows the link and follows its updates`() =
            runTest {
                client.on(TdApi.RequestQrCodeAuthentication::class, ok())
                val viewModel = onPhoneNumberEntry()

                send(viewModel, AuthEvent.LogInWithQrCode)
                tdlib(TdApiFixtures.waitOtherDeviceConfirmation("tg://login?token=first"))
                val first = viewModel.step<AuthStepUiState.QrCode>().link
                tdlib(TdApiFixtures.waitOtherDeviceConfirmation("tg://login?token=second"))

                assertEquals(1, client.sentOf<TdApi.RequestQrCodeAuthentication>().size)
                assertEquals("tg://login?token=first", first)
                assertEquals("tg://login?token=second", viewModel.step<AuthStepUiState.QrCode>().link)
                assertTrue(viewModel.state.canGoBack)
            }

        @Test
        fun `leaving QR login restarts TDLib and returns to phone entry with the number kept`() =
            runTest {
                client.on(TdApi.RequestQrCodeAuthentication::class, ok())
                client.on(TdApi.LogOut::class, ok())
                val viewModel = onPhoneNumberEntry()
                send(viewModel, AuthEvent.LogInWithQrCode)
                tdlib(TdApiFixtures.waitOtherDeviceConfirmation())

                send(viewModel, AuthEvent.Back)
                val restarting = viewModel.state.step
                listOf(TdApiFixtures.loggingOut(), TdApiFixtures.closed(), TdApiFixtures.waitTdlibParameters())
                    .forEach { tdlib(it) }
                val whileRestarting = viewModel.state.step
                tdlib(TdApiFixtures.waitPhoneNumber())

                assertEquals(1, client.sentOf<TdApi.LogOut>().size)
                assertEquals(AuthStepUiState.Loading, restarting)
                assertEquals(AuthStepUiState.Loading, whileRestarting)
                assertEquals(UK_NUMBER, viewModel.step<AuthStepUiState.PhoneNumber>().number)
            }
    }

    @Nested
    inner class QrCodeLeaving {
        @Test
        fun `a login arriving while leaving QR login does not count`() =
            runTest {
                client.on(TdApi.RequestQrCodeAuthentication::class, ok())
                client.on(TdApi.LogOut::class, ok())
                val viewModel = onPhoneNumberEntry()
                send(viewModel, AuthEvent.LogInWithQrCode)
                tdlib(TdApiFixtures.waitOtherDeviceConfirmation())
                val effects = effectsOf(viewModel)

                send(viewModel, AuthEvent.CancelQrCode)
                tdlib(TdApiFixtures.ready())

                assertTrue(effects.isEmpty())
                assertEquals(AuthStepUiState.Loading, viewModel.state.step)
            }
    }

    @Nested
    inner class Password {
        private suspend fun TestScope.onPassword(
            hint: String = "cat",
            hasRecoveryEmailAddress: Boolean = true,
        ): AuthViewModel {
            client.authState.value = AuthState.from(TdApiFixtures.waitPassword(hint, hasRecoveryEmailAddress))!!
            return viewModel()
        }

        @Test
        fun `shows the hint and sends the password`() =
            runTest {
                client.on(TdApi.CheckAuthenticationPassword::class, ok())
                val viewModel = onPassword()

                val hint = viewModel.step<AuthStepUiState.Password>().hint
                send(viewModel, AuthEvent.PasswordChanged("secret"), AuthEvent.SubmitPassword)

                assertEquals("cat", hint)
                assertEquals("secret", client.sentOf<TdApi.CheckAuthenticationPassword>().single().password)
            }

        @Test
        fun `a wrong password shows an inline error`() =
            runTest {
                client.on(TdApi.CheckAuthenticationPassword::class, failure("PASSWORD_HASH_INVALID"))
                val viewModel = onPassword()

                send(viewModel, AuthEvent.PasswordChanged("wrong"), AuthEvent.SubmitPassword)

                assertEquals(DomainError.PasswordInvalid, viewModel.step<AuthStepUiState.Password>().error)
            }

        @Test
        fun `an empty password is not sent`() =
            runTest {
                val viewModel = onPassword()

                send(viewModel, AuthEvent.SubmitPassword)

                assertTrue(client.sentOf<TdApi.CheckAuthenticationPassword>().isEmpty())
            }

        @Test
        fun `without a recovery email, forgot password explains it without asking Telegram`() =
            runTest {
                val viewModel = onPassword(hasRecoveryEmailAddress = false)

                send(viewModel, AuthEvent.ForgotPassword)

                assertTrue(viewModel.step<AuthStepUiState.Password>().showNoRecoveryEmailInfo)
                assertTrue(client.sentOf<TdApi.RequestAuthenticationPasswordRecovery>().isEmpty())
            }

        @Test
        fun `recovery goes through the emailed code and a new password`() =
            runTest {
                client.on(TdApi.RequestAuthenticationPasswordRecovery::class, ok())
                client.on(TdApi.CheckAuthenticationPasswordRecoveryCode::class, ok())
                client.on(TdApi.RecoverAuthenticationPassword::class, ok())
                val viewModel = onPassword()

                send(viewModel, AuthEvent.ForgotPassword)
                tdlib(TdApiFixtures.waitPassword("cat", true, false, "a***@example.org"))
                val codeStep = viewModel.step<AuthStepUiState.PasswordRecoveryCode>()
                send(viewModel, AuthEvent.RecoveryCodeChanged("123 456"), AuthEvent.SubmitRecoveryCode)
                send(
                    viewModel,
                    AuthEvent.NewPasswordChanged("new secret"),
                    AuthEvent.NewPasswordConfirmationChanged("new secre"),
                )
                val mismatch = viewModel.step<AuthStepUiState.NewPassword>()
                send(viewModel, AuthEvent.SubmitNewPassword)
                val sentWithMismatch = client.sentOf<TdApi.RecoverAuthenticationPassword>().size
                send(
                    viewModel,
                    AuthEvent.NewPasswordConfirmationChanged("new secret"),
                    AuthEvent.NewPasswordHintChanged("pet"),
                    AuthEvent.SubmitNewPassword,
                )

                val recover = client.sentOf<TdApi.RecoverAuthenticationPassword>().single()
                assertEquals("a***@example.org", codeStep.emailAddressPattern)
                assertEquals(
                    "123456",
                    client.sentOf<TdApi.CheckAuthenticationPasswordRecoveryCode>().single().recoveryCode,
                )
                assertTrue(mismatch.isMismatch)
                assertEquals(0, sentWithMismatch)
                assertEquals(
                    listOf("123456", "new secret", "pet"),
                    listOf(recover.recoveryCode, recover.newPassword, recover.newHint),
                )
            }

        @Test
        fun `a wrong recovery code shows an inline error`() =
            runTest {
                client.on(TdApi.RequestAuthenticationPasswordRecovery::class, ok())
                client.on(TdApi.CheckAuthenticationPasswordRecoveryCode::class, failure("CODE_INVALID"))
                val viewModel = onPassword()

                send(
                    viewModel,
                    AuthEvent.ForgotPassword,
                    AuthEvent.RecoveryCodeChanged("000000"),
                    AuthEvent.SubmitRecoveryCode,
                )

                assertEquals(DomainError.CodeInvalid, viewModel.step<AuthStepUiState.PasswordRecoveryCode>().error)
            }

        @Test
        fun `Telegram reporting no recovery email shows the explanation`() =
            runTest {
                client.on(TdApi.RequestAuthenticationPasswordRecovery::class, failure("PASSWORD_RECOVERY_NA"))
                val viewModel = onPassword()

                send(viewModel, AuthEvent.ForgotPassword)

                assertTrue(viewModel.step<AuthStepUiState.Password>().showNoRecoveryEmailInfo)
            }

        @Test
        fun `a new password answered with 406 asks to check it`() =
            runTest {
                client.on(TdApi.RequestAuthenticationPasswordRecovery::class, ok())
                client.on(TdApi.CheckAuthenticationPasswordRecoveryCode::class, ok())
                client.on(TdApi.RecoverAuthenticationPassword::class, failure("NEW_PASSWORD_INVALID", code = 406))
                val viewModel = onPassword()

                send(
                    viewModel,
                    AuthEvent.ForgotPassword,
                    AuthEvent.RecoveryCodeChanged("123456"),
                    AuthEvent.SubmitRecoveryCode,
                    AuthEvent.NewPasswordChanged("new secret"),
                    AuthEvent.NewPasswordConfirmationChanged("new secret"),
                    AuthEvent.SubmitNewPassword,
                )

                assertEquals(DomainError.InputRejected, viewModel.step<AuthStepUiState.NewPassword>().error)
            }

        @Test
        fun `back from recovery returns to the password`() =
            runTest {
                client.on(TdApi.RequestAuthenticationPasswordRecovery::class, ok())
                val viewModel = onPassword()
                send(viewModel, AuthEvent.ForgotPassword)

                send(viewModel, AuthEvent.Back)

                assertInstanceOf(AuthStepUiState.Password::class.java, viewModel.state.step)
            }
    }

    @Nested
    inner class Registration {
        private fun TestScope.onRegistration(mustShowTerms: Boolean = false): AuthViewModel {
            val terms = TdApi.TermsOfService(TdApiFixtures.formattedText("Be nice"), 16, mustShowTerms)
            client.authState.value = AuthState.WaitRegistration(terms)
            return viewModel()
        }

        @Test
        fun `the terms open by themselves when Telegram requires it, and accepting closes them`() =
            runTest {
                val viewModel = onRegistration(mustShowTerms = true)

                val opened = viewModel.step<AuthStepUiState.Registration>()
                send(viewModel, AuthEvent.AcceptTermsOfService)

                assertTrue(opened.isTermsOfServiceOpen)
                assertEquals("Be nice", opened.termsOfServiceText)
                assertEquals(16, opened.minUserAge)
                assertTrue(viewModel.step<AuthStepUiState.Registration>().termsAccepted)
                assertFalse(viewModel.step<AuthStepUiState.Registration>().isTermsOfServiceOpen)
            }

        @Test
        fun `registering needs a first name`() =
            runTest {
                val viewModel = onRegistration()

                send(viewModel, AuthEvent.TermsAcceptedChanged(true), AuthEvent.SubmitRegistration)

                assertEquals(
                    DomainError.FirstNameInvalid,
                    viewModel.step<AuthStepUiState.Registration>().firstNameError,
                )
                assertTrue(client.sentOf<TdApi.RegisterUser>().isEmpty())
            }

        @Test
        fun `registering without accepted terms shows them`() =
            runTest {
                val viewModel = onRegistration()

                send(viewModel, AuthEvent.FirstNameChanged("Ada"), AuthEvent.SubmitRegistration)

                assertTrue(viewModel.step<AuthStepUiState.Registration>().isTermsOfServiceOpen)
                assertTrue(client.sentOf<TdApi.RegisterUser>().isEmpty())
            }

        @Test
        fun `registering sends the trimmed names`() =
            runTest {
                client.on(TdApi.RegisterUser::class, ok())
                val viewModel = onRegistration()

                send(
                    viewModel,
                    AuthEvent.FirstNameChanged(" Ada "),
                    AuthEvent.LastNameChanged("Lovelace"),
                    AuthEvent.TermsAcceptedChanged(true),
                    AuthEvent.SubmitRegistration,
                )

                val sent = client.sentOf<TdApi.RegisterUser>().single()
                assertEquals("Ada" to "Lovelace", sent.firstName to sent.lastName)
            }

        @Test
        fun `a name rejected by Telegram shows an inline error`() =
            runTest {
                client.on(TdApi.RegisterUser::class, failure("LASTNAME_INVALID"))
                val viewModel = onRegistration()

                send(
                    viewModel,
                    AuthEvent.FirstNameChanged("Ada"),
                    AuthEvent.TermsAcceptedChanged(true),
                    AuthEvent.SubmitRegistration,
                )

                assertEquals(DomainError.LastNameInvalid, viewModel.step<AuthStepUiState.Registration>().lastNameError)
            }

        @Test
        fun `declining the terms un-accepts them, dismissing keeps the choice`() =
            runTest {
                val viewModel = onRegistration()

                send(viewModel, AuthEvent.TermsAcceptedChanged(true), AuthEvent.ShowTermsOfService)
                send(viewModel, AuthEvent.DismissTermsOfService)
                val dismissed = viewModel.step<AuthStepUiState.Registration>()
                send(viewModel, AuthEvent.ShowTermsOfService, AuthEvent.DeclineTermsOfService)
                val declined = viewModel.step<AuthStepUiState.Registration>()

                assertTrue(dismissed.termsAccepted)
                assertFalse(declined.termsAccepted)
                assertFalse(declined.isTermsOfServiceOpen)
            }

        @Test
        fun `names are capped at Telegram's limit`() =
            runTest {
                val viewModel = onRegistration()

                send(viewModel, AuthEvent.FirstNameChanged("a".repeat(100)))

                assertEquals(
                    AuthViewModel.MAX_NAME_LENGTH,
                    viewModel.step<AuthStepUiState.Registration>().firstName.length,
                )
            }
    }

    @Nested
    inner class Email {
        private suspend fun TestScope.onEmailCode(resetState: TdApi.EmailAddressResetState? = null): AuthViewModel {
            client.authState.value =
                AuthState.from(
                    TdApiFixtures.waitEmailCode("a***@example.org", 6, emailAddressResetState = resetState),
                )!!
            return viewModel()
        }

        @Test
        fun `an implausible email address is rejected without asking Telegram`() =
            runTest {
                client.authState.value = AuthState.WaitEmailAddress(allowAppleId = false, allowGoogleId = false)
                val viewModel = viewModel()

                send(viewModel, AuthEvent.EmailAddressChanged("ada.example.org"), AuthEvent.SubmitEmailAddress)

                assertEquals(DomainError.EmailInvalid, viewModel.step<AuthStepUiState.EmailAddress>().error)
                assertTrue(client.sentOf<TdApi.SetAuthenticationEmailAddress>().isEmpty())
            }

        @Test
        fun `the email address is sent, and a refused one shows an inline error`() =
            runTest {
                client.authState.value = AuthState.WaitEmailAddress(allowAppleId = false, allowGoogleId = false)
                client.on(TdApi.SetAuthenticationEmailAddress::class, failure("EMAIL_NOT_ALLOWED"))
                val viewModel = viewModel()

                send(viewModel, AuthEvent.EmailAddressChanged(" ada@example.org"), AuthEvent.SubmitEmailAddress)

                assertEquals(
                    "ada@example.org",
                    client.sentOf<TdApi.SetAuthenticationEmailAddress>().single().emailAddress,
                )
                assertEquals(DomainError.EmailNotAllowed, viewModel.step<AuthStepUiState.EmailAddress>().error)
            }

        @Test
        fun `a complete email code submits itself`() =
            runTest {
                client.on(TdApi.CheckAuthenticationEmailCode::class, ok())
                val viewModel = onEmailCode()

                val step = viewModel.step<AuthStepUiState.EmailCode>()
                send(viewModel, AuthEvent.EmailCodeChanged("123456"))

                val code = client.sentOf<TdApi.CheckAuthenticationEmailCode>().single().code
                assertEquals("a***@example.org" to 6, step.emailAddressPattern to step.codeLength)
                assertEquals("123456", (code as TdApi.EmailAddressAuthenticationCode).code)
            }

        @Test
        fun `typing past a complete email code does not send it again`() =
            runTest {
                client.on(TdApi.CheckAuthenticationEmailCode::class, failure("EMAIL_CODE_INVALID"))
                val viewModel = onEmailCode()

                send(viewModel, AuthEvent.EmailCodeChanged("123456"), AuthEvent.EmailCodeChanged("1234567"))

                assertEquals(1, client.sentOf<TdApi.CheckAuthenticationEmailCode>().size)
            }

        @Test
        fun `a wrong email code shows an inline error`() =
            runTest {
                client.on(TdApi.CheckAuthenticationEmailCode::class, failure("EMAIL_CODE_INVALID"))
                val viewModel = onEmailCode()

                send(viewModel, AuthEvent.EmailCodeChanged("123456"))

                assertEquals(DomainError.CodeInvalid, viewModel.step<AuthStepUiState.EmailCode>().error)
            }

        @Test
        fun `the code for an address entered in this login can be resent`() =
            runTest {
                client.authState.value = AuthState.WaitEmailAddress(allowAppleId = false, allowGoogleId = false)
                client.on(TdApi.SetAuthenticationEmailAddress::class, ok())
                client.on(TdApi.ResendAuthenticationCode::class, ok())
                val viewModel = viewModel()

                send(viewModel, AuthEvent.EmailAddressChanged("ada@example.org"), AuthEvent.SubmitEmailAddress)
                tdlib(TdApiFixtures.waitEmailCode("a***@example.org", 6))
                val step = viewModel.step<AuthStepUiState.EmailCode>()
                send(viewModel, AuthEvent.ResendEmailCode)

                assertTrue(step.canResendCode)
                assertEquals(1, client.sentOf<TdApi.ResendAuthenticationCode>().size)
            }

        // TDLib keeps no address to resend to for the address the account already had (td/telegram/AuthManager.cpp).
        @Test
        fun `the code for the account's existing address cannot be resent`() =
            runTest {
                val viewModel = onEmailCode()

                send(viewModel, AuthEvent.ResendEmailCode)

                assertFalse(viewModel.step<AuthStepUiState.EmailCode>().canResendCode)
                assertTrue(client.sentOf<TdApi.ResendAuthenticationCode>().isEmpty())
            }

        @Test
        fun `an expired email verification shows the expired code error`() =
            runTest {
                client.on(TdApi.CheckAuthenticationEmailCode::class, failure("EMAIL_VERIFY_EXPIRED"))
                val viewModel = onEmailCode()

                send(viewModel, AuthEvent.EmailCodeChanged("123456"))

                assertEquals(DomainError.CodeExpired, viewModel.step<AuthStepUiState.EmailCode>().error)
            }

        @Test
        fun `a rejected email code is not submitted again by itself`() =
            runTest {
                client.on(TdApi.CheckAuthenticationEmailCode::class, failure("EMAIL_CODE_INVALID"))
                val viewModel = onEmailCode()

                send(viewModel, AuthEvent.EmailCodeChanged("123456"), AuthEvent.EmailCodeChanged("1234567"))
                send(viewModel, AuthEvent.EmailCodeChanged("12345"), AuthEvent.EmailCodeChanged("123456"))

                assertEquals(1, client.sentOf<TdApi.CheckAuthenticationEmailCode>().size)
            }

        @Test
        fun `a reset is requested, and a pending reset counts down`() =
            runTest {
                client.on(TdApi.ResetAuthenticationEmailAddress::class, failure("TASK_ALREADY_EXISTS"))
                val viewModel = onEmailCode(TdApi.EmailAddressResetStateAvailable(3))
                val effects = effectsOf(viewModel)

                val available = viewModel.step<AuthStepUiState.EmailCode>().reset
                send(viewModel, AuthEvent.ResetEmailAddress)
                tdlib(
                    TdApiFixtures.waitEmailCode(
                        "a***@example.org",
                        6,
                        emailAddressResetState = TdApi.EmailAddressResetStatePending(3),
                    ),
                )
                val pending = viewModel.step<AuthStepUiState.EmailCode>().reset
                advanceSeconds(2)

                assertEquals(EmailResetUiState.Available(3), available)
                assertEquals(1, client.sentOf<TdApi.ResetAuthenticationEmailAddress>().size)
                assertTrue(effects.isEmpty())
                assertEquals(EmailResetUiState.Pending(3), pending)
                assertEquals(EmailResetUiState.Pending(1), viewModel.step<AuthStepUiState.EmailCode>().reset)
            }

        @Test
        fun `without a reset state the address cannot be reset`() =
            runTest {
                val viewModel = onEmailCode()

                send(viewModel, AuthEvent.ResetEmailAddress)

                assertNull(viewModel.step<AuthStepUiState.EmailCode>().reset)
                assertTrue(client.sentOf<TdApi.ResetAuthenticationEmailAddress>().isEmpty())
            }

        @Test
        fun `another address can be entered from the code step`() =
            runTest {
                client.on(TdApi.SetAuthenticationEmailAddress::class, ok())
                val viewModel = onEmailCode()

                send(viewModel, AuthEvent.ChangeEmailAddress)
                val changing = viewModel.step<AuthStepUiState.EmailAddress>()
                send(viewModel, AuthEvent.EmailAddressChanged("ada@example.org"), AuthEvent.SubmitEmailAddress)
                tdlib(TdApiFixtures.waitEmailCode("a***@example.org", 6))

                assertTrue(changing.isChangingAddress)
                assertEquals(1, client.sentOf<TdApi.SetAuthenticationEmailAddress>().size)
                assertTrue(viewModel.step<AuthStepUiState.EmailCode>().canResendCode)
            }
    }

    /** `Confirm` and `Reject` haptics (`docs/DESIGN_SYSTEM.md` §5) for codes and passwords Telegram checked. */
    @Nested
    inner class InputFeedback {
        private suspend fun TestScope.onCode(): AuthViewModel {
            client.authState.value = AuthState.from(waitCodeFor(UK_INTERNATIONAL))!!
            return viewModel()
        }

        private suspend fun TestScope.onPassword(): AuthViewModel {
            client.authState.value = AuthState.from(TdApiFixtures.waitPassword("cat", true))!!
            return viewModel()
        }

        @Test
        fun `an accepted code confirms`() =
            runTest {
                client.on(TdApi.CheckAuthenticationCode::class, ok())
                val viewModel = onCode()
                val effects = effectsOf(viewModel)

                send(viewModel, AuthEvent.CodeChanged("12345"))

                assertEquals(listOf(AuthEffect.InputAccepted), effects)
            }

        @Test
        fun `a wrong or expired code rejects`() =
            runTest {
                client.on(TdApi.CheckAuthenticationCode::class, failure("PHONE_CODE_INVALID"))
                val viewModel = onCode()
                val effects = effectsOf(viewModel)

                send(viewModel, AuthEvent.CodeChanged("12345"))
                client.on(TdApi.CheckAuthenticationCode::class, failure("PHONE_CODE_EXPIRED"))
                send(viewModel, AuthEvent.CodeChanged("12346"))

                assertEquals(listOf(AuthEffect.InputRejected, AuthEffect.InputRejected), effects)
            }

        @Test
        fun `a wrong password rejects, the right one confirms`() =
            runTest {
                client.on(TdApi.CheckAuthenticationPassword::class, failure("PASSWORD_HASH_INVALID"))
                val viewModel = onPassword()
                val effects = effectsOf(viewModel)

                send(viewModel, AuthEvent.PasswordChanged("wrong"), AuthEvent.SubmitPassword)
                client.on(TdApi.CheckAuthenticationPassword::class, ok())
                send(viewModel, AuthEvent.PasswordChanged("secret"), AuthEvent.SubmitPassword)

                assertEquals(listOf(AuthEffect.InputRejected, AuthEffect.InputAccepted), effects)
            }

        @Test
        fun `an accepted recovery code confirms`() =
            runTest {
                client.on(TdApi.RequestAuthenticationPasswordRecovery::class, ok())
                client.on(TdApi.CheckAuthenticationPasswordRecoveryCode::class, ok())
                val viewModel = onPassword()
                val effects = effectsOf(viewModel)

                send(
                    viewModel,
                    AuthEvent.ForgotPassword,
                    AuthEvent.RecoveryCodeChanged("123456"),
                    AuthEvent.SubmitRecoveryCode,
                )

                assertEquals(listOf(AuthEffect.InputAccepted), effects)
            }

        @Test
        fun `a code Telegram could not check gives no feedback on it`() =
            runTest {
                client.on(TdApi.CheckAuthenticationCode::class, failure("SOMETHING_NEW", code = 500))
                val viewModel = onCode()
                val effects = effectsOf(viewModel)

                send(viewModel, AuthEvent.CodeChanged("12345"))

                assertEquals(listOf(AuthEffect.ShowError(DomainError.Unknown(500, "SOMETHING_NEW"))), effects)
            }

        @Test
        fun `requesting a new code gives no feedback`() =
            runTest {
                client.on(TdApi.ResendAuthenticationCode::class, ok())
                val viewModel = onCode()
                val effects = effectsOf(viewModel)

                advanceSeconds(60)
                send(viewModel, AuthEvent.ResendCode)
                tdlib(waitCodeFor(UK_INTERNATIONAL))

                assertEquals(1, client.sentOf<TdApi.ResendAuthenticationCode>().size)
                assertTrue(effects.isEmpty())
            }
    }

    /** [AuthUiState.backStep]: the screen back leads to, for the predictive back gesture to show coming in. */
    @Nested
    inner class BackStep {
        /** Back synchronously, as the gesture's end does: the screen it shows must be the one the gesture showed. */
        private fun AuthViewModel.backLeadsToItsBackStep() {
            val backStep = state.backStep
            onEvent(AuthEvent.Back)
            assertEquals(backStep, state.step)
        }

        @Test
        fun `phone number entry leads back to the welcome screen`() =
            runTest {
                val viewModel = onPhoneNumberEntry()

                assertEquals(AuthStepUiState.Welcome, viewModel.state.backStep)
                viewModel.backLeadsToItsBackStep()
            }

        @Test
        fun `the code leads back to changing the number`() =
            runTest {
                client.authState.value = AuthState.from(waitCodeFor(UK_INTERNATIONAL))!!
                val viewModel = viewModel()

                assertTrue((viewModel.state.backStep as AuthStepUiState.PhoneNumber).isChangingNumber)
                viewModel.backLeadsToItsBackStep()
            }

        @Test
        fun `password recovery leads back to the password`() =
            runTest {
                client.on(TdApi.RequestAuthenticationPasswordRecovery::class, ok())
                client.authState.value = AuthState.from(TdApiFixtures.waitPassword("cat", true))!!
                val viewModel = viewModel()
                send(viewModel, AuthEvent.ForgotPassword)

                assertInstanceOf(AuthStepUiState.Password::class.java, viewModel.state.backStep)
                viewModel.backLeadsToItsBackStep()
            }

        @Test
        fun `QR login leads back to loading while TDLib restarts`() =
            runTest {
                client.on(TdApi.RequestQrCodeAuthentication::class, ok())
                client.on(TdApi.LogOut::class, ok())
                val viewModel = onPhoneNumberEntry()
                send(viewModel, AuthEvent.LogInWithQrCode)
                tdlib(TdApiFixtures.waitOtherDeviceConfirmation())

                assertEquals(AuthStepUiState.Loading, viewModel.state.backStep)
                viewModel.backLeadsToItsBackStep()
            }

        @Test
        fun `the welcome screen has none`() =
            runTest {
                val viewModel = viewModel()

                assertNull(viewModel.state.backStep)
            }
    }

    @Nested
    inner class End {
        @Test
        fun `Premium required explains and offers the support address, never a purchase`() =
            runTest {
                client.authState.value = AuthState.from(TdApiFixtures.waitPremiumPurchase())!!
                val viewModel = viewModel()
                val effects = effectsOf(viewModel)

                val step = viewModel.step<AuthStepUiState.PremiumRequired>()
                send(viewModel, AuthEvent.ContactSupport)

                assertEquals("support@example.org", step.supportEmailAddress)
                assertEquals(listOf(AuthEffect.SendEmail("support@example.org", "Login")), effects)
                assertTrue(
                    client.sent.none {
                        it is TdApi.CheckAuthenticationPremiumPurchase ||
                            it is TdApi.SetAuthenticationPremiumPurchaseTransaction
                    },
                )
            }

        @Test
        fun `Premium required offers another phone number`() =
            runTest {
                client.authState.value = AuthState.from(TdApiFixtures.waitPremiumPurchase())!!
                val viewModel = viewModel()

                send(viewModel, AuthEvent.ChangePhoneNumber)

                assertTrue(viewModel.step<AuthStepUiState.PhoneNumber>().isChangingNumber)
            }

        @Test
        fun `ready shows done and tells the app once`() =
            runTest {
                client.on(TdApi.CheckAuthenticationPassword::class, ok())
                client.authState.value = AuthState.from(TdApiFixtures.waitPassword())!!
                val viewModel = viewModel()
                val effects = effectsOf(viewModel)

                send(viewModel, AuthEvent.PasswordChanged("secret"), AuthEvent.SubmitPassword)
                tdlib(TdApiFixtures.ready())
                tdlib(TdApiFixtures.ready())

                assertEquals(AuthStepUiState.Done, viewModel.state.step)
                assertEquals(listOf(AuthEffect.InputAccepted, AuthEffect.Authorized), effects)
            }

        @Test
        fun `logging out shows progress, and closing starts over at the welcome screen`() =
            runTest {
                val viewModel = onPhoneNumberEntry()

                tdlib(TdApiFixtures.loggingOut())
                val loggingOut = viewModel.state.step
                tdlib(TdApiFixtures.closed())
                tdlib(TdApiFixtures.waitTdlibParameters())
                tdlib(TdApiFixtures.waitPhoneNumber())

                assertEquals(AuthStepUiState.LoggingOut, loggingOut)
                assertEquals(AuthStepUiState.Welcome, viewModel.state.step)
                send(viewModel, AuthEvent.StartMessaging)
                val phone = viewModel.step<AuthStepUiState.PhoneNumber>()
                assertEquals("", phone.number)
                // The current country is looked up again for the new session.
                assertEquals(UK_CALLING_CODE, phone.callingCode)
                assertEquals(2, client.sentOf<TdApi.GetCountryCode>().size)
            }
    }

    @Nested
    inner class ProcessDeath {
        @Test
        fun `typed input and local navigation are restored`() =
            runTest {
                client.on(TdApi.SetAuthenticationPhoneNumber::class, ok())
                val first = onPhoneNumberEntry()
                send(first, AuthEvent.CallingCodeChanged("49"), AuthEvent.PhoneNumberChanged("1512345"))

                val restored = viewModel(handle = savedState)

                val phone = restored.step<AuthStepUiState.PhoneNumber>()
                assertEquals("49" to "1512345", phone.callingCode to phone.number)
                assertEquals("DE", phone.country?.isoCode)
            }

        @Test
        fun `changing the number is restored, a typed code is not`() =
            runTest {
                client.authState.value = AuthState.from(TdApiFixtures.waitCode())!!
                val first = viewModel()
                send(first, AuthEvent.CodeChanged("12"))
                val withCode = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) })
                send(first, AuthEvent.ChangePhoneNumber)

                val changing = viewModel(handle = savedState)
                val coding = viewModel(handle = withCode)

                assertTrue(changing.step<AuthStepUiState.PhoneNumber>().isChangingNumber)
                assertFalse("12" in withCode.values())
                assertEquals("", coding.step<AuthStepUiState.Code>().code)
            }

        @Test
        fun `passwords and recovery codes are not saved, the recovery step comes back empty`() =
            runTest {
                client.on(TdApi.RequestAuthenticationPasswordRecovery::class, ok())
                client.authState.value = AuthState.from(TdApiFixtures.waitPassword(hasRecoveryEmailAddress = true))!!
                val first = viewModel()
                send(first, AuthEvent.PasswordChanged("secret"))
                val passwordSaved = "secret" in savedState.values()
                send(first, AuthEvent.ForgotPassword, AuthEvent.RecoveryCodeChanged("123456"))

                val restored = viewModel(handle = savedState)

                assertFalse(passwordSaved)
                assertFalse("123456" in savedState.values())
                assertEquals("", restored.step<AuthStepUiState.PasswordRecoveryCode>().code)
            }

        @Test
        fun `an email code is not saved`() =
            runTest {
                client.authState.value = AuthState.from(TdApiFixtures.waitEmailCode("a***@example.org", 6))!!
                val first = viewModel()
                send(first, AuthEvent.EmailCodeChanged("123"))

                val restored = viewModel(handle = savedState)

                assertFalse("123" in savedState.values())
                assertEquals("", restored.step<AuthStepUiState.EmailCode>().code)
            }

        @Test
        fun `registration input is restored`() =
            runTest {
                client.authState.value = AuthState.from(TdApiFixtures.waitRegistration())!!
                val first = viewModel()
                send(first, AuthEvent.FirstNameChanged("Ada"), AuthEvent.TermsAcceptedChanged(true))

                val restored = viewModel(handle = savedState).step<AuthStepUiState.Registration>()

                assertEquals("Ada", restored.firstName)
                assertTrue(restored.termsAccepted)
            }

        @Test
        fun `nothing typed is kept after login`() =
            runTest {
                val viewModel = onPhoneNumberEntry()

                tdlib(TdApiFixtures.ready())

                assertEquals("", savedState.get<String>("auth.phoneNumber"))
                assertEquals(AuthStepUiState.Done, viewModel.state.step)
            }
    }
}
