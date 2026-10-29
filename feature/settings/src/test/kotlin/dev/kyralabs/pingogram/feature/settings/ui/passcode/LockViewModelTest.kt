package dev.kyralabs.pingogram.feature.settings.ui.passcode

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.PasscodeSettings
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.domain.repository.LockState
import dev.kyralabs.pingogram.core.testing.FakeAppLockRepository
import dev.kyralabs.pingogram.core.testing.FakePasscodeRepository
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class LockViewModelTest {
    private val appLock = FakeAppLockRepository(state = LockState.Locked, passcode = "2580")
    private val passcodes = FakePasscodeRepository(passcode = "2580")
    private var biometricsAvailable = true
    private var screenLockAvailable = false
    private val availability =
        object : BiometricAvailability {
            override fun canAuthenticate() = biometricsAvailable

            override fun canUseScreenLock() = screenLockAvailable
        }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel() = LockViewModel(appLock, passcodes, availability).also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: LockViewModel): List<LockEffect> {
        val effects = mutableListOf<LockEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.enter(
        viewModel: LockViewModel,
        passcode: String,
    ) {
        passcode.forEach { viewModel.input.append(it, PasscodeType.Pin) }
        viewModel.onEvent(LockEvent.Submit)
        runCurrent()
    }

    private val LockViewModel.state get() = uiState.value

    @Test
    fun `asks for the passcode of its type, offering biometrics where they are on`() =
        runTest {
            passcodes.setPasscode("long password", PasscodeType.Password)

            val state = viewModel().state

            assertEquals(PasscodeType.Password, state.entry.type)
            assertTrue(state.biometricUnlock)
            assertTrue(state.promptBiometrics)
            assertNull(state.entry.message)
        }

    @Test
    fun `offers no biometrics where they are off or the device cannot`() =
        runTest {
            biometricsAvailable = false
            assertFalse(viewModel().state.biometricUnlock)

            biometricsAvailable = true
            passcodes.setBiometricUnlock(false)
            val state = viewModel().state
            assertFalse(state.biometricUnlock)
            assertFalse(state.promptBiometrics)
        }

    @Test
    fun `the phone's screen lock unlocks where it is on and set, alone or with biometrics`() =
        runTest {
            screenLockAvailable = true
            assertEquals(ScreenLock.Off, viewModel().state.screenLock)

            passcodes.setDeviceCredentialUnlock(true)
            assertEquals(ScreenLock.Also, viewModel().state.screenLock)

            biometricsAvailable = false
            val only = viewModel().state
            assertEquals(ScreenLock.Only, only.screenLock)
            assertTrue(only.biometricUnlock)
            assertTrue(only.promptBiometrics)

            screenLockAvailable = false
            val none = viewModel().state
            assertEquals(ScreenLock.Off, none.screenLock)
            assertFalse(none.biometricUnlock)
        }

    @Test
    fun `the right passcode unlocks`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            enter(viewModel, "2580")

            assertEquals(LockState.Unlocked, appLock.state.value)
            assertEquals(listOf(LockEffect.Unlocked), effects)
            assertEquals("", viewModel.input.text)
        }

    @Test
    fun `a wrong passcode says so, clears the entry and counts as a rejection`() =
        runTest {
            val viewModel = viewModel()

            enter(viewModel, "1111")

            assertEquals(PasscodeMessage.Wrong, viewModel.state.entry.message)
            assertEquals(1, viewModel.state.entry.rejections)
            assertEquals("", viewModel.input.text)
            assertEquals(LockState.Locked, appLock.state.value)
        }

    @Test
    fun `an empty entry is not checked`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(LockEvent.Submit)
            runCurrent()

            assertTrue(appLock.checked.isEmpty())
        }

    @Test
    fun `too many attempts count the wait down, refusing entries meanwhile`() =
        runTest {
            val viewModel = viewModel()
            appLock.wait = 2.seconds

            enter(viewModel, "1111")
            assertEquals(PasscodeMessage.Wait(2), viewModel.state.entry.message)
            assertFalse(viewModel.state.entry.canSubmit)

            appLock.wait = 1.seconds
            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(PasscodeMessage.Wait(1), viewModel.state.entry.message)

            appLock.wait = Duration.ZERO
            advanceTimeBy(1.seconds)
            runCurrent()
            assertNull(viewModel.state.entry.message)
            assertTrue(viewModel.state.entry.canSubmit)
        }

    @Test
    fun `a wait still running when the lock screen opens shows at once`() =
        runTest {
            appLock.wait = 30.seconds

            val viewModel = viewModel()

            assertEquals(PasscodeMessage.Wait(30), viewModel.state.entry.message)
            appLock.wait = Duration.ZERO
            advanceTimeBy(1.seconds)
            runCurrent()
        }

    @Test
    fun `a check that fails says why`() =
        runTest {
            val viewModel = viewModel()
            appLock.failure = DomainError.Unknown(0, "Disk unavailable")

            enter(viewModel, "2580")

            assertEquals(
                PasscodeMessage.Failed(DomainError.Unknown(0, "Disk unavailable")),
                viewModel.state.entry.message,
            )
        }

    @Test
    fun `biometrics unlock, and the prompt shows by itself once per lock`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(LockEvent.BiometricPromptShown)
            runCurrent()
            assertFalse(viewModel.state.promptBiometrics)

            viewModel.onEvent(LockEvent.BiometricSucceeded)
            runCurrent()
            assertEquals(LockState.Unlocked, appLock.state.value)
            assertEquals(listOf(LockEffect.Unlocked), effects)

            appLock.setState(LockState.Locked)
            runCurrent()
            assertTrue(viewModel.state.promptBiometrics)
        }

    @Test
    fun `every new lock starts over with an empty entry`() =
        runTest {
            val viewModel = viewModel()
            enter(viewModel, "1111")
            viewModel.input.append('1', PasscodeType.Pin)
            appLock.setState(LockState.Unlocked)
            runCurrent()

            appLock.setState(LockState.Locked)
            runCurrent()

            assertNull(viewModel.state.entry.message)
            assertEquals("", viewModel.input.text)
        }

    @Test
    fun `a forgotten passcode logs out once confirmed`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(LockEvent.ForgotPasscode)
            runCurrent()
            assertTrue(viewModel.state.showForgetDialog)
            assertEquals(0, appLock.forgetCount)

            viewModel.onEvent(LockEvent.ConfirmForget)
            runCurrent()

            assertEquals(1, appLock.forgetCount)
            assertFalse(viewModel.state.showForgetDialog)
            assertFalse(viewModel.state.isLoggingOut)
        }

    @Test
    fun `dismissing keeps the passcode`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(LockEvent.ForgotPasscode)
            viewModel.onEvent(LockEvent.DismissForget)
            runCurrent()

            assertFalse(viewModel.state.showForgetDialog)
            assertEquals(0, appLock.forgetCount)
        }

    @Test
    fun `a failed logout shows why and stays locked`() =
        runTest {
            appLock.forgetOutcome = Outcome.Failure(DomainError.Network)
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(LockEvent.ConfirmForget)
            runCurrent()

            assertEquals(listOf(LockEffect.ShowError(DomainError.Network)), effects)
            assertEquals(LockState.Locked, appLock.state.value)
            assertFalse(viewModel.state.isLoggingOut)
        }

    @Test
    fun `the settings decide the entry before the first lock`() =
        runTest {
            val settings = FakePasscodeRepository(PasscodeSettings(type = PasscodeType.Password), passcode = "abcd")

            val viewModel = LockViewModel(appLock, settings) { true }.also { runCurrent() }

            assertEquals(PasscodeType.Password, viewModel.state.entry.type)
        }
}
