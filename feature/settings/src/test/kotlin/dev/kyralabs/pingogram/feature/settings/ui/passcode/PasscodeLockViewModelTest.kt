package dev.kyralabs.pingogram.feature.settings.ui.passcode

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AutoLock
import dev.kyralabs.pingogram.core.domain.model.PasscodeSettings
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.testing.FakeAppLockRepository
import dev.kyralabs.pingogram.core.testing.FakePasscodeRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class PasscodeLockViewModelTest {
    private val appLock = FakeAppLockRepository(passcode = "2580")
    private var biometricsAvailable = true

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(passcodes: FakePasscodeRepository) =
        PasscodeLockViewModel(passcodes, appLock) { biometricsAvailable }.also { runCurrent() }

    private fun TestScope.send(
        viewModel: PasscodeLockViewModel,
        event: PasscodeLockEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    private fun TestScope.enter(
        viewModel: PasscodeLockViewModel,
        passcode: String,
    ) {
        passcode.forEach { viewModel.input.append(it, PasscodeType.Pin) }
        send(viewModel, PasscodeLockEvent.Submit)
    }

    private val PasscodeLockViewModel.state get() = uiState.value

    private val on =
        PasscodeLockUiState.On(
            autoLock = AutoLock.OneMinute,
            biometricAvailable = true,
            biometricUnlock = true,
            hideContent = true,
        )

    @Test
    fun `is off without a passcode`() =
        runTest {
            assertEquals(PasscodeLockUiState.Off, viewModel(FakePasscodeRepository()).state)
        }

    @Test
    fun `a passcode set from here needs no entering`() =
        runTest {
            val passcodes = FakePasscodeRepository()
            val viewModel = viewModel(passcodes)

            passcodes.setPasscode("1234", PasscodeType.Pin)
            runCurrent()

            assertEquals(on, viewModel.state)
        }

    @Test
    fun `with a passcode, the settings show only once it is entered`() =
        runTest {
            val viewModel = viewModel(FakePasscodeRepository(passcode = "2580"))
            assertTrue(viewModel.state is PasscodeLockUiState.Verify)

            enter(viewModel, "1111")
            val verify = viewModel.state as PasscodeLockUiState.Verify
            assertEquals(PasscodeMessage.Wrong, verify.entry.message)

            enter(viewModel, "2580")
            assertEquals(on, viewModel.state)
            assertEquals(listOf("1111", "2580"), appLock.checked)
        }

    @Test
    fun `entering asks for the passcode's type`() =
        runTest {
            val passcodes = FakePasscodeRepository(PasscodeSettings(type = PasscodeType.Password), passcode = "abcd")

            val verify = viewModel(passcodes).state as PasscodeLockUiState.Verify

            assertEquals(PasscodeType.Password, verify.entry.type)
        }

    @Test
    fun `changes the settings and shows them`() =
        runTest {
            val passcodes = FakePasscodeRepository()
            val viewModel = viewModel(passcodes)
            passcodes.setPasscode("1234", PasscodeType.Pin)

            send(viewModel, PasscodeLockEvent.SelectAutoLock(AutoLock.Immediately))
            send(viewModel, PasscodeLockEvent.SetBiometricUnlock(false))
            send(viewModel, PasscodeLockEvent.SetHideContent(false))

            assertEquals(
                on.copy(autoLock = AutoLock.Immediately, biometricUnlock = false, hideContent = false),
                viewModel.state,
            )
            assertEquals(AutoLock.Immediately, passcodes.settings.value.autoLock)
        }

    @Test
    fun `hides biometrics where the device cannot, and checks again when the page shows again`() =
        runTest {
            biometricsAvailable = false
            val passcodes = FakePasscodeRepository()
            val viewModel = viewModel(passcodes)
            passcodes.setPasscode("1234", PasscodeType.Pin)
            runCurrent()
            assertFalse((viewModel.state as PasscodeLockUiState.On).biometricAvailable)

            biometricsAvailable = true
            send(viewModel, PasscodeLockEvent.Refresh)

            assertTrue((viewModel.state as PasscodeLockUiState.On).biometricAvailable)
        }

    @Test
    fun `turning off asks first, then removes the passcode`() =
        runTest {
            val passcodes = FakePasscodeRepository()
            val viewModel = viewModel(passcodes)
            passcodes.setPasscode("1234", PasscodeType.Pin)

            send(viewModel, PasscodeLockEvent.TurnOff)
            assertEquals(on.copy(showTurnOffDialog = true), viewModel.state)
            assertTrue(passcodes.settings.value.isSet)

            send(viewModel, PasscodeLockEvent.ConfirmTurnOff)

            assertFalse(passcodes.settings.value.isSet)
            assertEquals(PasscodeLockUiState.Off, viewModel.state)
        }

    @Test
    fun `dismissing keeps the passcode`() =
        runTest {
            val passcodes = FakePasscodeRepository()
            val viewModel = viewModel(passcodes)
            passcodes.setPasscode("1234", PasscodeType.Pin)

            send(viewModel, PasscodeLockEvent.TurnOff)
            send(viewModel, PasscodeLockEvent.DismissTurnOff)

            assertEquals(on, viewModel.state)
        }

    @Test
    fun `a setting that cannot be saved shows why`() =
        runTest {
            val passcodes = FakePasscodeRepository()
            val viewModel = viewModel(passcodes)
            passcodes.setPasscode("1234", PasscodeType.Pin)
            val effects = mutableListOf<PasscodeLockEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
            passcodes.failure = DomainError.Unknown(0, "Disk full")

            send(viewModel, PasscodeLockEvent.SetHideContent(false))

            assertEquals(listOf(PasscodeLockEffect.ShowError(DomainError.Unknown(0, "Disk full"))), effects)
            assertEquals(on, viewModel.state)
        }
}
