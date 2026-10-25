package dev.kyralabs.pingogram.feature.settings.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.FrequentContactsRepository
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
class FrequentContactsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class Contacts : FrequentContactsRepository {
        override val suggests = MutableStateFlow(true)
        var failure: DomainError? = null

        override suspend fun setSuggests(suggests: Boolean): Outcome<Unit> {
            failure?.let { return Outcome.Failure(it) }
            this.suggests.value = suggests
            return Outcome.Success(Unit)
        }
    }

    private val contacts = Contacts()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `unknown until read, then turned off and saved`() =
        runTest(dispatcher) {
            val viewModel = FrequentContactsViewModel(contacts)
            assertNull(viewModel.suggests.value)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.suggests.collect {} }
            runCurrent()
            assertEquals(true, viewModel.suggests.value)

            viewModel.onCheckedChange(false)
            runCurrent()

            assertEquals(false, viewModel.suggests.value)
            assertEquals(false, contacts.suggests.value)
        }

    @Test
    fun `a change Telegram refuses is an error`() =
        runTest(dispatcher) {
            contacts.failure = DomainError.Network
            val viewModel = FrequentContactsViewModel(contacts)

            viewModel.errors.test {
                viewModel.onCheckedChange(false)
                runCurrent()

                assertEquals(DomainError.Network, awaitItem())
            }
        }
}
