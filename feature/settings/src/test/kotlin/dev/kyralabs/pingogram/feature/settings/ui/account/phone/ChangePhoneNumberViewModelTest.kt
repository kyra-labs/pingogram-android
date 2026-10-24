package dev.kyralabs.pingogram.feature.settings.ui.account.phone

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibCountryRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibPhoneNumberChangeRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.model.AuthCodeType
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
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
class ChangePhoneNumberViewModelTest {
    private val client = FakeTdlibClient()
    private val savedState = SavedStateHandle()

    private val britain = TdApi.CountryInfo("GB", "United Kingdom", "United Kingdom", "", false, arrayOf("44"))
    private val germany = TdApi.CountryInfo("DE", "Germany", "Germany", "", false, arrayOf("49"))

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        client.on(TdApi.GetCountries::class, TdResult.Success(TdApi.Countries(arrayOf(britain, germany))))
        client.on(TdApi.GetCountryCode::class, TdResult.Success(TdApi.Text("GB")))
        client.on(TdApi.GetPhoneNumberInfo::class) { request ->
            val digits = request.phoneNumberPrefix.filter(Char::isDigit)
            val country = listOf(britain, germany).firstOrNull { digits.startsWith(it.callingCodes.first()) }
            TdResult.Success(
                TdApi.PhoneNumberInfo(country, country?.callingCodes?.first().orEmpty(), "---- ------", false),
            )
        }
        client.on(TdApi.SendPhoneNumberCode::class, TdResult.Success(smsCode(timeout = 3)))
        client.on(TdApi.CheckPhoneNumberCode::class, TdResult.Success(TdApi.Ok()))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun smsCode(timeout: Int) =
        TdApi.AuthenticationCodeInfo(
            "+44 7911 123456",
            TdApi.AuthenticationCodeTypeSms(5),
            TdApi.AuthenticationCodeTypeCall(5),
            timeout,
        )

    // Through the real TDLib repositories on FakeTdlibClient, as the app wires them.
    private suspend fun TestScope.viewModel(handle: SavedStateHandle = savedState): ChangePhoneNumberViewModel {
        val users = TdlibUserRepository(client, backgroundScope)
        client.emit(TdApiFixtures.updateUser(TdApiFixtures.user(id = 7, phoneNumber = "4915112345678")))
        client.emit(TdApiFixtures.myIdOption(7))
        return ChangePhoneNumberViewModel(
            handle,
            TdlibCountryRepository(client),
            TdlibPhoneNumberChangeRepository(client),
            users,
        ).also { runCurrent() }
    }

    private fun TestScope.effectsOf(viewModel: ChangePhoneNumberViewModel): List<ChangePhoneNumberEffect> {
        val effects = mutableListOf<ChangePhoneNumberEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: ChangePhoneNumberViewModel,
        vararg events: ChangePhoneNumberEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    private fun TestScope.atCode(viewModel: ChangePhoneNumberViewModel) =
        send(
            viewModel,
            ChangePhoneNumberEvent.Start,
            ChangePhoneNumberEvent.NumberChanged("7911123456"),
            ChangePhoneNumberEvent.SubmitNumber,
        )

    private val ChangePhoneNumberViewModel.state get() = uiState.value

    @Test
    fun `starts with what changing the number does and the current number`() =
        runTest {
            val viewModel = viewModel()

            assertEquals(ChangePhoneNumberStep.Intro, viewModel.state.step)
            assertEquals("4915112345678", viewModel.state.currentNumber)
        }

    @Test
    fun `the new number starts with the calling code of the user's country`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, ChangePhoneNumberEvent.Start)

            assertEquals(ChangePhoneNumberStep.Number, viewModel.state.step)
            assertEquals("44", viewModel.state.newNumber.callingCode)
            assertEquals(
                "GB",
                viewModel.state.newNumber.country
                    ?.isoCode,
            )
        }

    @Test
    fun `a new number gets a code, whose resend countdown runs down`() =
        runTest {
            val viewModel = viewModel()

            atCode(viewModel)

            assertEquals("+447911123456", client.sentOf<TdApi.SendPhoneNumberCode>().single().phoneNumber)
            assertEquals(ChangePhoneNumberStep.Code, viewModel.state.step)
            assertEquals(AuthCodeType.Sms(5), viewModel.state.code?.type)
            assertEquals(3L, viewModel.state.code?.resendInSeconds)
            assertFalse(viewModel.state.code!!.canResend)
            advanceTimeBy(3.seconds)
            runCurrent()
            assertTrue(viewModel.state.code!!.canResend)
        }

    @Test
    fun `a number too short is refused before Telegram is asked`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ChangePhoneNumberEvent.Start, ChangePhoneNumberEvent.NumberChanged("12"))
            send(viewModel, ChangePhoneNumberEvent.SubmitNumber)

            assertEquals(DomainError.PhoneNumberInvalid, viewModel.state.newNumber.error)
            assertTrue(client.sentOf<TdApi.SendPhoneNumberCode>().isEmpty())
            assertEquals(listOf(ChangePhoneNumberEffect.InputRejected), effects)
        }

    @Test
    fun `a number another account has shows under its field`() =
        runTest {
            client.on(
                TdApi.SendPhoneNumberCode::class,
                TdResult.Failure(TdError(400, "PHONE_NUMBER_OCCUPIED")),
            )
            val viewModel = viewModel()

            atCode(viewModel)

            assertEquals(ChangePhoneNumberStep.Number, viewModel.state.step)
            assertEquals(DomainError.PhoneNumberOccupied, viewModel.state.newNumber.error)
            assertFalse(viewModel.state.isWorking)
        }

    @Test
    fun `a complete code is checked by itself and changes the number`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            atCode(viewModel)

            send(viewModel, ChangePhoneNumberEvent.CodeChanged("12345"))

            assertEquals("12345", client.sentOf<TdApi.CheckPhoneNumberCode>().single().code)
            assertEquals(ChangePhoneNumberStep.Done, viewModel.state.step)
            assertEquals("+44 7911 123456", viewModel.state.changedTo)
            assertEquals(listOf(ChangePhoneNumberEffect.Changed), effects)
        }

    @Test
    fun `a wrong code shows under its field and is not sent again by itself`() =
        runTest {
            client.on(TdApi.CheckPhoneNumberCode::class, TdResult.Failure(TdError(400, "PHONE_CODE_INVALID")))
            val viewModel = viewModel()
            atCode(viewModel)

            send(viewModel, ChangePhoneNumberEvent.CodeChanged("12345"))
            assertEquals(DomainError.CodeInvalid, viewModel.state.code?.error)
            send(viewModel, ChangePhoneNumberEvent.CodeChanged("1234"), ChangePhoneNumberEvent.CodeChanged("12345"))

            assertEquals(1, client.sentOf<TdApi.CheckPhoneNumberCode>().size)
            assertEquals(ChangePhoneNumberStep.Code, viewModel.state.step)
        }

    @Test
    fun `a flood wait counts down before anything can be sent again`() =
        runTest {
            client.on(TdApi.SendPhoneNumberCode::class, TdResult.Failure(TdError(429, "FLOOD_WAIT_2")))
            val viewModel = viewModel()

            atCode(viewModel)

            assertEquals(2L, viewModel.state.floodWaitSeconds)
            assertFalse(viewModel.state.canSubmit)
            advanceTimeBy(2.seconds)
            runCurrent()
            assertNull(viewModel.state.floodWaitSeconds)
            assertTrue(viewModel.state.canSubmit)
        }

    @Test
    fun `a new code is asked for once the countdown ran out`() =
        runTest {
            client.on(TdApi.ResendPhoneNumberCode::class, TdResult.Success(smsCode(timeout = 0)))
            val viewModel = viewModel()
            atCode(viewModel)

            send(viewModel, ChangePhoneNumberEvent.ResendCode)
            assertTrue(client.sentOf<TdApi.ResendPhoneNumberCode>().isEmpty())
            advanceTimeBy(3.seconds)
            runCurrent()
            send(viewModel, ChangePhoneNumberEvent.ResendCode)

            assertEquals(1, client.sentOf<TdApi.ResendPhoneNumberCode>().size)
        }

    @Test
    fun `back returns a step at a time`() =
        runTest {
            val viewModel = viewModel()
            atCode(viewModel)

            send(viewModel, ChangePhoneNumberEvent.Back)
            assertEquals(ChangePhoneNumberStep.Number, viewModel.state.step)
            assertNull(viewModel.state.code)
            send(viewModel, ChangePhoneNumberEvent.Back)
            assertEquals(ChangePhoneNumberStep.Intro, viewModel.state.step)
        }

    @Test
    fun `after process death the number typed comes back, a code step as the number`() =
        runTest {
            atCode(viewModel())

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

            assertEquals(ChangePhoneNumberStep.Number, restored.state.step)
            assertEquals("44" to "7911123456", restored.state.newNumber.let { it.callingCode to it.number })
            assertNull(restored.state.code)
        }
}
