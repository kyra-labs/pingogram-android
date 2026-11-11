package dev.kyralabs.pingogram.feature.settings.ui.passkeys

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibPasskeyRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSessionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibTwoStepVerificationRepository
import dev.kyralabs.pingogram.core.domain.model.PasskeyRegistration
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.ui.passkey.PasskeyResult
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.settings.R
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
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The Passkeys page through the real TDLib repositories on [FakeTdlibClient], as the app wires them. */
@OptIn(ExperimentalCoroutinesApi::class)
class PasskeysViewModelTest {
    private val client = FakeTdlibClient()
    private val savedState = SavedStateHandle()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        client.on(
            TdApi.GetPasswordState::class,
            TdResult.Success(TdApi.PasswordState(true, "", false, false, null, "", 0)),
        )
        client.on(
            TdApi.GetActiveSessions::class,
            TdResult.Success(TdApi.Sessions(arrayOf(session(1), session(2)), 180)),
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(handle: SavedStateHandle = savedState): PasskeysViewModel =
        PasskeysViewModel(
            handle,
            TdlibPasskeyRepository(client),
            TdlibTwoStepVerificationRepository(client),
            TdlibSessionRepository(client),
            testTimeFormats(),
        ).also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: PasskeysViewModel): List<PasskeysEffect> {
        val effects = mutableListOf<PasskeysEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun answerPasskeys(vararg passkeys: TdApi.Passkey) =
        client.on(TdApi.GetLoginPasskeys::class, TdResult.Success(TdApi.Passkeys(arrayOf(*passkeys))))

    private val PasskeysViewModel.passkeys
        get() = (uiState.value.passkeys as PasskeysState.Loaded).passkeys

    @Test
    fun `lists the passkeys with when they were added and used, and what else protects the account`() =
        runTest {
            answerPasskeys(
                passkey("a", "Google Password Manager", used = true),
                passkey("b", "Bitwarden", used = false),
            )

            val viewModel = viewModel()

            assertEquals(listOf("Google Password Manager", "Bitwarden"), viewModel.passkeys.map { it.name })
            assertEquals(listOf("Wed", "8:41 AM"), viewModel.passkeys[0].details.plainParts())
            assertEquals(R.string.settings_passkey_never_used, viewModel.passkeys[1].details.lastResource())
            assertEquals(
                AccountProtection(twoStepVerification = true, sessionCount = 2),
                viewModel.uiState.value.protection,
            )
        }

    @Test
    fun `a failure shows, and trying again loads them`() =
        runTest {
            client.on(TdApi.GetLoginPasskeys::class, TdResult.Failure(TdError(500, "Internal")))
            val viewModel = viewModel()
            assertTrue(viewModel.uiState.value.passkeys is PasskeysState.Failed)

            answerPasskeys(passkey("a", "Bitwarden"))
            viewModel.onEvent(PasskeysEvent.Retry)
            runCurrent()

            assertEquals(listOf("a"), viewModel.passkeys.map { it.id })
        }

    @Test
    fun `adding hands Telegram's request to the password managers, then gives Telegram their answer`() =
        runTest {
            answerPasskeys()
            client.on(TdApi.GetPasskeyParameters::class, TdResult.Success(TdApi.Text(REQUEST)))
            client.on(TdApi.AddLoginPasskey::class, TdResult.Success(passkey("new", "Google Password Manager")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(PasskeysEvent.Add)
            runCurrent()
            assertEquals(listOf(PasskeysEffect.CreatePasskey(REQUEST)), effects)
            assertTrue(viewModel.uiState.value.isAdding)

            viewModel.onEvent(PasskeysEvent.Created(PasskeyResult.Done(PasskeyRegistration("{}", byteArrayOf(1, 2)))))
            runCurrent()

            val sent = client.sentOf<TdApi.AddLoginPasskey>().single()
            assertEquals("{}", sent.clientData)
            assertArrayEquals(byteArrayOf(1, 2), sent.attestationObject)
            assertEquals(listOf("new"), viewModel.passkeys.map { it.id })
            assertFalse(viewModel.uiState.value.isAdding)
            assertEquals(PasskeysEffect.Added("Google Password Manager"), effects.last())
        }

    @Test
    fun `a cancelled sheet sends nothing, and a password manager that cannot make it says so`() =
        runTest {
            answerPasskeys()
            client.on(TdApi.GetPasskeyParameters::class, TdResult.Success(TdApi.Text(REQUEST)))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(PasskeysEvent.Add)
            runCurrent()
            viewModel.onEvent(PasskeysEvent.Created(PasskeyResult.Cancelled))
            viewModel.onEvent(PasskeysEvent.Add)
            runCurrent()
            viewModel.onEvent(PasskeysEvent.Created(PasskeyResult.Unavailable))
            runCurrent()

            assertTrue(client.sentOf<TdApi.AddLoginPasskey>().isEmpty())
            assertFalse(viewModel.uiState.value.isAdding)
            assertEquals(PasskeysEffect.Unavailable, effects.last())
        }

    @Test
    fun `a refused request says why and adds nothing`() =
        runTest {
            answerPasskeys()
            client.on(TdApi.GetPasskeyParameters::class, TdResult.Failure(TdError(429, "FLOOD_WAIT_30")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(PasskeysEvent.Add)
            runCurrent()

            assertEquals(listOf(PasskeysEffect.ShowError(DomainError.FloodWait(30))), effects)
            assertFalse(viewModel.uiState.value.isAdding)
        }

    @Test
    fun `removing a passkey asks first, then removes it`() =
        runTest {
            answerPasskeys(passkey("a", "Bitwarden"))
            client.on(TdApi.RemoveLoginPasskey::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(PasskeysEvent.Remove(viewModel.passkeys.single()))
            assertEquals(RemovePasskeyDialog("a", "Bitwarden"), viewModel.uiState.value.dialog)
            assertTrue(client.sentOf<TdApi.RemoveLoginPasskey>().isEmpty())
            viewModel.onEvent(PasskeysEvent.Confirm)
            runCurrent()

            assertEquals("a", client.sentOf<TdApi.RemoveLoginPasskey>().single().passkeyId)
            assertTrue(viewModel.passkeys.isEmpty())
            assertNull(viewModel.uiState.value.dialog)
            assertEquals(listOf(PasskeysEffect.Removed("Bitwarden")), effects)
        }

    @Test
    fun `a refused removal keeps the passkey and says why`() =
        runTest {
            answerPasskeys(passkey("a", "Bitwarden"))
            client.on(TdApi.RemoveLoginPasskey::class, TdResult.Failure(TdError(400, "PASSKEY_INVALID")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(PasskeysEvent.Remove(viewModel.passkeys.single()))
            viewModel.onEvent(PasskeysEvent.Confirm)
            runCurrent()

            assertEquals(listOf("a"), viewModel.passkeys.map { it.id })
            assertTrue(effects.single() is PasskeysEffect.ShowError)
            assertTrue(
                viewModel.uiState.value.removingIds
                    .isEmpty(),
            )
        }

    @Test
    fun `dismissing the question sends nothing, and the question survives process death`() =
        runTest {
            answerPasskeys(passkey("a", "Bitwarden"))
            val viewModel = viewModel()
            viewModel.onEvent(PasskeysEvent.Remove(viewModel.passkeys.single()))

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))
            assertEquals(RemovePasskeyDialog("a", "Bitwarden"), restored.uiState.value.dialog)
            restored.onEvent(PasskeysEvent.Dismiss)

            assertNull(restored.uiState.value.dialog)
            assertTrue(client.sentOf<TdApi.RemoveLoginPasskey>().isEmpty())
        }

    private companion object {
        const val REQUEST = """{"challenge":"abc"}"""
        const val DAY = 86_400L
        const val HOUR = 3_600L

        fun passkey(
            id: String,
            name: String,
            used: Boolean = true,
        ) = TdApi.Passkey(
            id,
            name,
            (TEST_NOW.epochSecond - 4 * DAY).toInt(),
            if (used) (TEST_NOW.epochSecond - HOUR).toInt() else 0,
            0,
        )

        fun session(id: Long) =
            TdApi.Session(
                id,
                id == 1L,
                false,
                false,
                true,
                true,
                TdApi.SessionDeviceTypeAndroid(),
                1,
                "App",
                "1.0",
                false,
                "Phone",
                "Android",
                "15",
                0,
                0,
                "",
                "",
            )
    }
}

/** The plain strings inside a text built of resources, in order. */
private fun UiText.plainParts(): List<String> =
    when (this) {
        is UiText.Plain -> listOf(plainSpaces(text))
        is UiText.Resource -> args.flatMap { if (it is UiText) it.plainParts() else listOf(plainSpaces(it.toString())) }
        else -> error("Unexpected $this")
    }

/** The resource of the last text inside a text built of resources. */
private fun UiText.lastResource(): Int =
    when (this) {
        is UiText.Resource -> (args.lastOrNull() as? UiText)?.lastResource() ?: id
        else -> error("Unexpected $this")
    }
