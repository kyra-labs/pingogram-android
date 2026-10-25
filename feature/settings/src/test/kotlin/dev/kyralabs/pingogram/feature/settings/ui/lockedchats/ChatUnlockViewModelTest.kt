package dev.kyralabs.pingogram.feature.settings.ui.lockedchats

import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.domain.repository.PasscodeCheck
import dev.kyralabs.pingogram.core.testing.FakeAppLockRepository
import dev.kyralabs.pingogram.core.testing.FakeChatLockRepository
import dev.kyralabs.pingogram.core.testing.FakePasscodeRepository
import dev.kyralabs.pingogram.feature.settings.ui.passcode.PasscodeMessage
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ChatUnlockViewModelTest {
    private val chatLock = FakeChatLockRepository(locked = setOf(5L), passcode = "2580")
    private val appLock = FakeAppLockRepository(passcode = "2580")
    private val passcodes = FakePasscodeRepository(passcode = "2580")
    private var biometricsAvailable = true

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel() =
        ChatUnlockViewModel(chatLock, appLock, passcodes) { biometricsAvailable }.also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: ChatUnlockViewModel): List<ChatUnlockEffect> {
        val effects = mutableListOf<ChatUnlockEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.enter(
        viewModel: ChatUnlockViewModel,
        passcode: String,
    ) {
        passcode.forEach { viewModel.input.append(it, PasscodeType.Pin) }
        viewModel.onEvent(ChatUnlockEvent.Submit)
        runCurrent()
    }

    @Test
    fun `the right passcode opens the chats`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            enter(viewModel, "2580")

            assertTrue(chatLock.unlocked.value)
            assertEquals(listOf(ChatUnlockEffect.Unlocked), effects)
        }

    @Test
    fun `a wrong passcode says so, and a wait counts down`() =
        runTest {
            val viewModel = viewModel()

            enter(viewModel, "1111")
            assertEquals(PasscodeMessage.Wrong, viewModel.uiState.value.entry.message)
            assertFalse(chatLock.unlocked.value)

            chatLock.wrongCheck = PasscodeCheck.Wait(30.seconds)
            appLock.wait = 30.seconds
            enter(viewModel, "1111")
            assertEquals(PasscodeMessage.Wait(30), viewModel.uiState.value.entry.message)
        }

    @Test
    fun `offers biometrics where they are on, prompting once until the chats lock again`() =
        runTest {
            val viewModel = viewModel()
            assertTrue(viewModel.uiState.value.biometricUnlock)
            assertTrue(viewModel.uiState.value.promptBiometrics)

            viewModel.onEvent(ChatUnlockEvent.BiometricPromptShown)
            runCurrent()
            assertFalse(viewModel.uiState.value.promptBiometrics)

            viewModel.onEvent(ChatUnlockEvent.BiometricSucceeded)
            runCurrent()
            assertTrue(chatLock.unlocked.value)

            chatLock.setUnlocked(false)
            runCurrent()
            assertTrue(viewModel.uiState.value.promptBiometrics)
        }

    @Test
    fun `without biometrics on the device, only the passcode`() =
        runTest {
            biometricsAvailable = false

            val state = viewModel().uiState.value

            assertFalse(state.biometricUnlock)
            assertFalse(state.promptBiometrics)
        }
}
