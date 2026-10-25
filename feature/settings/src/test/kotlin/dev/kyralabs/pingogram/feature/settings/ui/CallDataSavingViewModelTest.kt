package dev.kyralabs.pingogram.feature.settings.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.CallDataSaving
import dev.kyralabs.pingogram.core.domain.repository.CallSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallDataSavingViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class Settings : CallSettingsRepository {
        override val dataSaving = MutableStateFlow(CallDataSaving.Never)
        var failure: DomainError? = null

        override suspend fun setDataSaving(saving: CallDataSaving): Outcome<Unit> {
            failure?.let { return Outcome.Failure(it) }
            dataSaving.value = saving
            return Outcome.Success(Unit)
        }
    }

    private val settings = Settings()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `nothing until read, then the choice, and a new choice is saved`() =
        runTest(dispatcher) {
            val viewModel = CallDataSavingViewModel(settings)
            assertEquals(null, viewModel.dataSaving.value)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.dataSaving.collect {} }
            runCurrent()
            assertEquals(CallDataSaving.Never, viewModel.dataSaving.value)

            viewModel.onSelect(CallDataSaving.OnMobileData)
            runCurrent()

            assertEquals(CallDataSaving.OnMobileData, viewModel.dataSaving.value)
            assertEquals(CallDataSaving.OnMobileData, settings.dataSaving.value)
        }

    @Test
    fun `a choice that cannot be saved is an error`() =
        runTest(dispatcher) {
            settings.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = CallDataSavingViewModel(settings)

            viewModel.errors.test {
                viewModel.onSelect(CallDataSaving.Always)
                runCurrent()

                assertEquals(DomainError.Unknown(0, "Disk full"), awaitItem())
            }
        }
}
