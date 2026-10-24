package dev.kyralabs.pingogram.feature.settings.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.LinkPreviewSettingsRepository
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
class SecretChatPreviewsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class Settings : LinkPreviewSettingsRepository {
        override val secretChatPreviews = MutableStateFlow(false)
        var failure: DomainError? = null

        override suspend fun setSecretChatPreviews(enabled: Boolean): Outcome<Unit> {
            failure?.let { return Outcome.Failure(it) }
            secretChatPreviews.value = enabled
            return Outcome.Success(Unit)
        }
    }

    private val settings = Settings()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `off until turned on, then saved`() =
        runTest(dispatcher) {
            val viewModel = SecretChatPreviewsViewModel(settings)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.enabled.collect {} }
            runCurrent()
            assertEquals(false, viewModel.enabled.value)

            viewModel.onCheckedChange(true)
            runCurrent()

            assertEquals(true, viewModel.enabled.value)
            assertEquals(true, settings.secretChatPreviews.value)
        }

    @Test
    fun `a change that cannot be saved is an error`() =
        runTest(dispatcher) {
            settings.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = SecretChatPreviewsViewModel(settings)

            viewModel.errors.test {
                viewModel.onCheckedChange(true)
                runCurrent()

                assertEquals(DomainError.Unknown(0, "Disk full"), awaitItem())
            }
        }
}
