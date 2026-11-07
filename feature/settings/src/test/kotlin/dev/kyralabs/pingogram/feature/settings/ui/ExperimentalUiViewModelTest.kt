package dev.kyralabs.pingogram.feature.settings.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.testing.FakeExperimentalUiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExperimentalUiViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val settings = FakeExperimentalUiRepository()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `nothing until read, then off until turned on, which is saved`() =
        runTest(dispatcher) {
            val viewModel = ExperimentalUiViewModel(settings)
            assertNull(viewModel.enabled.value)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.enabled.collect {} }
            runCurrent()
            assertEquals(false, viewModel.enabled.value)

            viewModel.onCheckedChange(true)
            runCurrent()

            assertEquals(true, viewModel.enabled.value)
            assertEquals(true, settings.enabled.value)
        }

    @Test
    fun `a change that cannot be saved is an error, and the setting stays`() =
        runTest(dispatcher) {
            settings.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = ExperimentalUiViewModel(settings)

            viewModel.errors.test {
                viewModel.onCheckedChange(true)
                runCurrent()

                assertEquals(DomainError.Unknown(0, "Disk full"), awaitItem())
            }
            assertEquals(false, settings.enabled.value)
        }
}
