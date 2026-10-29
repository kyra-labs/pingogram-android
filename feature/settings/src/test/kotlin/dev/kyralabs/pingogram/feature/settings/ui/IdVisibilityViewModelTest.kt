package dev.kyralabs.pingogram.feature.settings.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.IdVisibilityRepository
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
class IdVisibilityViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class Settings : IdVisibilityRepository {
        override val shown = MutableStateFlow(false)
        var failure: DomainError? = null

        override suspend fun setShown(shown: Boolean): Outcome<Unit> {
            failure?.let { return Outcome.Failure(it) }
            this.shown.value = shown
            return Outcome.Success(Unit)
        }
    }

    private val settings = Settings()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `nothing until read, then hidden until shown, which is saved`() =
        runTest(dispatcher) {
            val viewModel = IdVisibilityViewModel(settings)
            assertNull(viewModel.shown.value)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.shown.collect {} }
            runCurrent()
            assertEquals(false, viewModel.shown.value)

            viewModel.onCheckedChange(true)
            runCurrent()

            assertEquals(true, viewModel.shown.value)
            assertEquals(true, settings.shown.value)
        }

    @Test
    fun `a change that cannot be saved is an error, and the setting stays`() =
        runTest(dispatcher) {
            settings.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = IdVisibilityViewModel(settings)

            viewModel.errors.test {
                viewModel.onCheckedChange(true)
                runCurrent()

                assertEquals(DomainError.Unknown(0, "Disk full"), awaitItem())
            }
            assertEquals(false, settings.shown.value)
        }
}
