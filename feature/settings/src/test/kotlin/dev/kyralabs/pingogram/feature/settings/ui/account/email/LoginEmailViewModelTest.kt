package dev.kyralabs.pingogram.feature.settings.ui.account.email

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibLoginEmailRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
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
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class LoginEmailViewModelTest {
    private val client = FakeTdlibClient()
    private val savedState = SavedStateHandle()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        answerLoginEmail("a****@g****.com")
        client.on(
            TdApi.SetLoginEmailAddress::class,
            TdResult.Success(TdApi.EmailAddressAuthenticationCodeInfo("a****@e******.org", 6)),
        )
        client.on(TdApi.CheckLoginEmailAddressCode::class, TdResult.Success(TdApi.Ok()))
        client.on(
            TdApi.ResendLoginEmailAddressCode::class,
            TdResult.Success(TdApi.EmailAddressAuthenticationCodeInfo("a****@e******.org", 6)),
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun answerLoginEmail(pattern: String) =
        client.on(
            TdApi.GetPasswordState::class,
            TdResult.Success(TdApi.PasswordState(false, "", false, false, null, pattern, 0)),
        )

    // Through the real TDLib repository on FakeTdlibClient, as the app wires it.
    private fun TestScope.viewModel(handle: SavedStateHandle = savedState) =
        LoginEmailViewModel(handle, TdlibLoginEmailRepository(client)).also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: LoginEmailViewModel): List<LoginEmailEffect> {
        val effects = mutableListOf<LoginEmailEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: LoginEmailViewModel,
        vararg events: LoginEmailEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    private fun TestScope.atCode(viewModel: LoginEmailViewModel) =
        send(
            viewModel,
            LoginEmailEvent.Start,
            LoginEmailEvent.AddressChanged("ada@example.org"),
            LoginEmailEvent.SubmitAddress,
        )

    private val LoginEmailViewModel.state get() = uiState.value

    @Test
    fun `shows the login email as Telegram's pattern`() =
        runTest {
            val viewModel = viewModel()

            assertFalse(viewModel.state.isLoading)
            assertEquals("a****@g****.com", viewModel.state.current)
        }

    @Test
    fun `an account without a login email cannot change it`() =
        runTest {
            answerLoginEmail("")
            val viewModel = viewModel()

            send(viewModel, LoginEmailEvent.Start)

            assertNull(viewModel.state.current)
            assertEquals(LoginEmailStep.Intro, viewModel.state.step)
        }

    @Test
    fun `a new address gets a code`() =
        runTest {
            val viewModel = viewModel()

            atCode(viewModel)

            assertEquals("ada@example.org", client.sentOf<TdApi.SetLoginEmailAddress>().single().newLoginEmailAddress)
            assertEquals(LoginEmailStep.Code, viewModel.state.step)
            assertEquals("a****@e******.org" to 6, viewModel.state.sentTo to viewModel.state.codeLength)
        }

    @Test
    fun `an address that is no email is refused before Telegram is asked`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, LoginEmailEvent.Start, LoginEmailEvent.AddressChanged("ada"), LoginEmailEvent.SubmitAddress)

            assertEquals(DomainError.EmailInvalid, viewModel.state.addressError)
            assertTrue(client.sentOf<TdApi.SetLoginEmailAddress>().isEmpty())
            assertEquals(listOf(LoginEmailEffect.InputRejected), effects)
        }

    @Test
    fun `an address Telegram does not take shows under its field`() =
        runTest {
            client.on(TdApi.SetLoginEmailAddress::class, TdResult.Failure(TdError(400, "EMAIL_NOT_ALLOWED")))
            val viewModel = viewModel()

            atCode(viewModel)

            assertEquals(LoginEmailStep.Address, viewModel.state.step)
            assertEquals(DomainError.EmailNotAllowed, viewModel.state.addressError)
        }

    @Test
    fun `a complete code is checked by itself and changes the login email`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            atCode(viewModel)

            send(viewModel, LoginEmailEvent.CodeChanged("123456"))

            val sent = client.sentOf<TdApi.CheckLoginEmailAddressCode>().single().code
            assertEquals("123456", (sent as TdApi.EmailAddressAuthenticationCode).code)
            assertEquals(LoginEmailStep.Done, viewModel.state.step)
            assertEquals("a****@e******.org", viewModel.state.current)
            assertEquals(listOf(LoginEmailEffect.Changed), effects)
        }

    @Test
    fun `a wrong code shows under its field and is not sent again by itself`() =
        runTest {
            client.on(TdApi.CheckLoginEmailAddressCode::class, TdResult.Failure(TdError(400, "CODE_INVALID")))
            val viewModel = viewModel()
            atCode(viewModel)

            send(viewModel, LoginEmailEvent.CodeChanged("123456"))
            assertEquals(DomainError.CodeInvalid, viewModel.state.codeError)
            send(viewModel, LoginEmailEvent.CodeChanged("12345"), LoginEmailEvent.CodeChanged("123456"))

            assertEquals(1, client.sentOf<TdApi.CheckLoginEmailAddressCode>().size)
        }

    @Test
    fun `a new code can be asked for`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            atCode(viewModel)

            send(viewModel, LoginEmailEvent.ResendCode)

            assertEquals(1, client.sentOf<TdApi.ResendLoginEmailAddressCode>().size)
            assertEquals(listOf(LoginEmailEffect.CodeResent), effects)
        }

    @Test
    fun `a flood wait counts down before anything can be sent again`() =
        runTest {
            client.on(TdApi.SetLoginEmailAddress::class, TdResult.Failure(TdError(429, "FLOOD_WAIT_2")))
            val viewModel = viewModel()

            atCode(viewModel)

            assertFalse(viewModel.state.canSubmit)
            advanceTimeBy(2.seconds)
            runCurrent()
            assertTrue(viewModel.state.canSubmit)
        }

    @Test
    fun `back returns a step at a time`() =
        runTest {
            val viewModel = viewModel()
            atCode(viewModel)

            send(viewModel, LoginEmailEvent.Back)
            assertEquals(LoginEmailStep.Address, viewModel.state.step)
            send(viewModel, LoginEmailEvent.Back)
            assertEquals(LoginEmailStep.Intro, viewModel.state.step)
        }

    @Test
    fun `after process death the address typed comes back, a code step as the address`() =
        runTest {
            atCode(viewModel())

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

            assertEquals(LoginEmailStep.Address, restored.state.step)
            assertEquals("ada@example.org", restored.state.address)
        }
}
