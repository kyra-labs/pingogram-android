package dev.kyralabs.pingogram.feature.settings.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.DirectShareRepository
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DirectShareViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class Settings : DirectShareRepository {
        override val enabled = MutableStateFlow(true)
        var failure: DomainError? = null

        override suspend fun setEnabled(enabled: Boolean): Outcome<Unit> {
            failure?.let { return Outcome.Failure(it) }
            this.enabled.value = enabled
            return Outcome.Success(Unit)
        }
    }

    private val settings = Settings()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `nothing until read, then offered until turned off, which is saved`() =
        runTest(dispatcher) {
            val viewModel = DirectShareViewModel(settings)
            assertNull(viewModel.enabled.value)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.enabled.collect {} }
            runCurrent()
            assertEquals(true, viewModel.enabled.value)

            viewModel.onCheckedChange(false)
            runCurrent()

            assertEquals(false, viewModel.enabled.value)
            assertEquals(false, settings.enabled.value)
        }

    @Test
    fun `a change that cannot be saved is an error, and the setting stays`() =
        runTest(dispatcher) {
            settings.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = DirectShareViewModel(settings)

            viewModel.errors.test {
                viewModel.onCheckedChange(false)
                runCurrent()

                assertEquals(DomainError.Unknown(0, "Disk full"), awaitItem())
            }
            assertEquals(true, settings.enabled.value)
        }
}
