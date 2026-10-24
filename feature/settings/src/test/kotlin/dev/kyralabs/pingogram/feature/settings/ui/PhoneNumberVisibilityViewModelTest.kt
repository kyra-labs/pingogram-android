package dev.kyralabs.pingogram.feature.settings.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.PhoneNumberVisibilityRepository
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
class PhoneNumberVisibilityViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class Settings : PhoneNumberVisibilityRepository {
        override val hidden = MutableStateFlow(false)
        var failure: DomainError? = null

        override suspend fun setHidden(hidden: Boolean): Outcome<Unit> {
            failure?.let { return Outcome.Failure(it) }
            this.hidden.value = hidden
            return Outcome.Success(Unit)
        }
    }

    private val settings = Settings()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `nothing until read, then shown until hidden, which is saved`() =
        runTest(dispatcher) {
            val viewModel = PhoneNumberVisibilityViewModel(settings)
            assertNull(viewModel.hidden.value)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.hidden.collect {} }
            runCurrent()
            assertEquals(false, viewModel.hidden.value)

            viewModel.onCheckedChange(true)
            runCurrent()

            assertEquals(true, viewModel.hidden.value)
            assertEquals(true, settings.hidden.value)
        }

    @Test
    fun `a change that cannot be saved is an error, and the setting stays`() =
        runTest(dispatcher) {
            settings.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = PhoneNumberVisibilityViewModel(settings)

            viewModel.errors.test {
                viewModel.onCheckedChange(true)
                runCurrent()

                assertEquals(DomainError.Unknown(0, "Disk full"), awaitItem())
            }
            assertEquals(false, settings.hidden.value)
        }
}
