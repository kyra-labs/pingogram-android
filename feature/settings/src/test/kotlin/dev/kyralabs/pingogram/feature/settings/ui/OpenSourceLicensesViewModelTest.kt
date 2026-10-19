package dev.kyralabs.pingogram.feature.settings.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.feature.settings.ui.FakeOpenSourceLicensesRepository.Companion.MATERIAL3
import dev.kyralabs.pingogram.feature.settings.ui.FakeOpenSourceLicensesRepository.Companion.TDLIB
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OpenSourceLicensesViewModelTest {
    private val repository = FakeOpenSourceLicensesRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): OpenSourceLicensesViewModel =
        OpenSourceLicensesViewModel(repository).also { runCurrent() }

    @Test
    fun `loads until the list is read`() =
        runTest {
            repository.gate = CompletableDeferred()

            val viewModel = viewModel()

            assertEquals(OpenSourceLicensesUiState.Loading, viewModel.uiState.value)
        }

    @Test
    fun `shows each library with its version and the names of its licenses, in the repository's order`() =
        runTest {
            val viewModel = viewModel()

            assertEquals(
                OpenSourceLicensesUiState.Content(
                    persistentListOf(
                        OpenSourceLibraryItem(
                            "org.drinkless:tdlib",
                            "TDLib",
                            "a94b83d67a",
                            "Boost Software License 1.0",
                        ),
                        OpenSourceLibraryItem(
                            id = "androidx.compose.material3:material3",
                            name = "Compose Material3 Components",
                            version = "1.5.0-beta01",
                            licenses = "Apache License 2.0, Boost Software License 1.0",
                        ),
                    ),
                ),
                viewModel.uiState.value,
            )
        }

    @Test
    fun `a list that cannot be read shows the error`() =
        runTest {
            repository.error = DomainError.Unknown(0, "unreadable")

            val viewModel = viewModel()

            assertEquals(OpenSourceLicensesUiState.Error(DomainError.Unknown(0, "unreadable")), viewModel.uiState.value)
        }

    @Test
    fun `retry reads the list again after it failed`() =
        runTest {
            repository.error = DomainError.Unknown(0, "unreadable")
            val viewModel = viewModel()
            repository.error = null
            repository.libraries = listOf(MATERIAL3)

            viewModel.onEvent(OpenSourceLicensesEvent.Retry)
            runCurrent()

            val content = viewModel.uiState.value as OpenSourceLicensesUiState.Content
            assertEquals(listOf(MATERIAL3.id), content.libraries.map { it.id })
        }

    @Test
    fun `retry does nothing while the list shows`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(OpenSourceLicensesEvent.Retry)
            runCurrent()

            assertEquals(1, repository.requests)
            assertEquals(
                listOf(
                    TDLIB.id,
                    MATERIAL3.id,
                ),
                (viewModel.uiState.value as OpenSourceLicensesUiState.Content).libraries.map {
                    it.id
                },
            )
        }
}
