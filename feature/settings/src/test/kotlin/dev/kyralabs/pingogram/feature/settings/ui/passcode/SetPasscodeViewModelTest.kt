package dev.kyralabs.pingogram.feature.settings.ui.passcode

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetPasscodeViewModelTest {
    private val passcodes = FakePasscodeRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel() = SetPasscodeViewModel(passcodes).also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: SetPasscodeViewModel): List<SetPasscodeEffect> {
        val effects = mutableListOf<SetPasscodeEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: SetPasscodeViewModel,
        event: SetPasscodeEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    /** Types [passcode] as the user would for the entry's type, then submits it. */
    private fun TestScope.enter(
        viewModel: SetPasscodeViewModel,
        passcode: String,
    ) {
        val type = viewModel.uiState.value.entry.type
        passcode.forEach { viewModel.input.append(it, type) }
        send(viewModel, SetPasscodeEvent.Submit)
    }

    private val SetPasscodeViewModel.state get() = uiState.value

    @Test
    fun `starts with choosing a PIN`() =
        runTest {
            assertEquals(SetPasscodeUiState(), viewModel().state)
        }

    @Test
    fun `a PIN entered twice is set`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            enter(viewModel, "2580")
            assertEquals(SetPasscodeStep.Confirm, viewModel.state.step)
            assertEquals("", viewModel.input.text)
            assertFalse(passcodes.settings.value.isSet)

            enter(viewModel, "2580")

            assertEquals(listOf(SetPasscodeEffect.Saved), effects)
            assertEquals("2580", passcodes.passcode)
            assertEquals(PasscodeType.Pin, passcodes.settings.value.type)
        }

    @Test
    fun `a password entered twice is set`() =
        runTest {
            val viewModel = viewModel()
            send(viewModel, SetPasscodeEvent.SwitchType)
            assertEquals(PasscodeType.Password, viewModel.state.entry.type)

            enter(viewModel, "correct horse")
            enter(viewModel, "correct horse")

            assertEquals("correct horse", passcodes.passcode)
            assertEquals(PasscodeType.Password, passcodes.settings.value.type)
        }

    @Test
    fun `a passcode too short is refused`() =
        runTest {
            val viewModel = viewModel()

            enter(viewModel, "123")

            assertEquals(SetPasscodeStep.Choose, viewModel.state.step)
            assertEquals(PasscodeMessage.Invalid, viewModel.state.entry.message)
            assertEquals(1, viewModel.state.entry.rejections)
        }

    @Test
    fun `a PIN stops at its longest`() =
        runTest {
            val viewModel = viewModel()

            "1234567890".forEach { viewModel.input.append(it, PasscodeType.Pin) }

            assertEquals("12345678", viewModel.input.text)
        }

    @Test
    fun `a second entry that differs starts over`() =
        runTest {
            val viewModel = viewModel()

            enter(viewModel, "2580")
            enter(viewModel, "0852")

            assertEquals(SetPasscodeStep.Choose, viewModel.state.step)
            assertEquals(PasscodeMessage.Mismatch, viewModel.state.entry.message)
            assertFalse(passcodes.settings.value.isSet)

            enter(viewModel, "1111")
            enter(viewModel, "1111")
            assertEquals("1111", passcodes.passcode)
        }

    @Test
    fun `back from the second entry returns to choosing`() =
        runTest {
            val viewModel = viewModel()
            enter(viewModel, "2580")

            send(viewModel, SetPasscodeEvent.Back)

            assertEquals(SetPasscodeStep.Choose, viewModel.state.step)
            assertNull(viewModel.state.entry.message)
        }

    @Test
    fun `the type switches only while choosing`() =
        runTest {
            val viewModel = viewModel()
            enter(viewModel, "2580")

            send(viewModel, SetPasscodeEvent.SwitchType)

            assertEquals(PasscodeType.Pin, viewModel.state.entry.type)
        }

    @Test
    fun `a passcode that cannot be saved says why and starts over`() =
        runTest {
            passcodes.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            enter(viewModel, "2580")
            enter(viewModel, "2580")

            assertTrue(effects.isEmpty())
            assertEquals(SetPasscodeStep.Choose, viewModel.state.step)
            assertEquals(PasscodeMessage.Failed(DomainError.Unknown(0, "Disk full")), viewModel.state.entry.message)
            assertFalse(viewModel.state.entry.isChecking)
        }
}
