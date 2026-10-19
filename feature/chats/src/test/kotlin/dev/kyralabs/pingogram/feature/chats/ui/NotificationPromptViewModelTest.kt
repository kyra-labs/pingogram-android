package dev.kyralabs.pingogram.feature.chats.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.NotificationPrompt
import dev.kyralabs.pingogram.core.domain.repository.NotificationPromptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPromptViewModelTest {
    private val repository = FakeNotificationPromptRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val notAsked = NotificationPromptEvent.PermissionChecked(granted = false, shouldShowRationale = false)
    private val refusedOnce = NotificationPromptEvent.PermissionChecked(granted = false, shouldShowRationale = true)
    private val granted = NotificationPromptEvent.PermissionChecked(granted = true, shouldShowRationale = false)

    @Test
    fun `hidden until the screen has read the permission`() =
        runTest {
            NotificationPromptViewModel(repository).uiState.test {
                assertEquals(NotificationPromptUiState.Hidden, awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun `asks while notifications are not allowed, and hides once they are`() =
        runTest {
            val viewModel = NotificationPromptViewModel(repository)

            viewModel.uiState.test {
                assertEquals(NotificationPromptUiState.Hidden, awaitItem())
                viewModel.onEvent(notAsked)
                assertEquals(NotificationPromptUiState.Ask, awaitItem())
                viewModel.onEvent(granted)
                assertEquals(NotificationPromptUiState.Hidden, awaitItem())
            }
        }

    @Test
    fun `allow saves the request and shows the system dialog`() =
        runTest {
            val viewModel = NotificationPromptViewModel(repository)

            viewModel.effects.test {
                viewModel.onEvent(NotificationPromptEvent.Allow)
                assertEquals(NotificationPromptEffect.RequestPermission, awaitItem())
            }
            runCurrent()
            assertTrue(repository.prompt.value.wasRequested)
        }

    @Test
    fun `after a first refusal it asks again, after a final one it offers the settings`() =
        runTest {
            repository.prompt.value = NotificationPrompt(wasRequested = true)
            val viewModel = NotificationPromptViewModel(repository)

            viewModel.uiState.test {
                awaitItem()
                viewModel.onEvent(refusedOnce)
                assertEquals(NotificationPromptUiState.Ask, awaitItem())
                viewModel.onEvent(notAsked)
                assertEquals(NotificationPromptUiState.Blocked, awaitItem())
            }
            viewModel.effects.test {
                viewModel.onEvent(NotificationPromptEvent.OpenSettings)
                assertEquals(NotificationPromptEffect.OpenSettings, awaitItem())
            }
        }

    @Test
    fun `not now hides it for good`() =
        runTest {
            val viewModel = NotificationPromptViewModel(repository)

            viewModel.uiState.test {
                awaitItem()
                viewModel.onEvent(notAsked)
                assertEquals(NotificationPromptUiState.Ask, awaitItem())
                viewModel.onEvent(NotificationPromptEvent.NotNow)
                assertEquals(NotificationPromptUiState.Hidden, awaitItem())
            }
            assertEquals(NotificationPrompt(isDismissed = true), repository.prompt.value)
        }

    @Test
    fun `answers that cannot be saved leave it as it is`() =
        runTest {
            repository.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = NotificationPromptViewModel(repository)

            viewModel.uiState.test {
                awaitItem()
                viewModel.onEvent(notAsked)
                assertEquals(NotificationPromptUiState.Ask, awaitItem())
                viewModel.onEvent(NotificationPromptEvent.NotNow)
                expectNoEvents()
            }
        }
}

/** [NotificationPromptRepository] in memory; [failure] makes saving fail. */
class FakeNotificationPromptRepository : NotificationPromptRepository {
    override val prompt = MutableStateFlow(NotificationPrompt())

    var failure: DomainError? = null

    override suspend fun dismiss() = save { it.copy(isDismissed = true) }

    override suspend fun setRequested() = save { it.copy(wasRequested = true) }

    private fun save(change: (NotificationPrompt) -> NotificationPrompt): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        prompt.update(change)
        return Outcome.Success(Unit)
    }
}
