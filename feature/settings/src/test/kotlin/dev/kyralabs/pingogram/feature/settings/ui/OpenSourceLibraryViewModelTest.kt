package dev.kyralabs.pingogram.feature.settings.ui

import app.cash.turbine.test
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
class OpenSourceLibraryViewModelTest {
    private val repository = FakeOpenSourceLicensesRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(libraryId: String = TDLIB.id): OpenSourceLibraryViewModel =
        OpenSourceLibraryViewModel(libraryId, repository).also { runCurrent() }

    @Test
    fun `loads until the library is read`() =
        runTest {
            repository.gate = CompletableDeferred()

            val viewModel = viewModel()

            assertEquals(OpenSourceLibraryUiState.Loading, viewModel.uiState.value)
        }

    @Test
    fun `shows the library with its licenses' texts reflowed for the screen`() =
        runTest {
            val viewModel = viewModel()

            assertEquals(
                OpenSourceLibraryUiState.Content(
                    id = "org.drinkless:tdlib",
                    name = "TDLib",
                    version = "a94b83d67a",
                    isMavenArtifact = true,
                    description = "Telegram Database Library.",
                    developers = "Aliaksei Levin, Arseny Smirnov",
                    website = "https://github.com/tdlib/td",
                    licenses =
                        persistentListOf(
                            LicenseItem(
                                id = "BSL-1.0",
                                name = "Boost Software License 1.0",
                                text =
                                    "Boost Software License - Version 1.0 - August 17th, 2003\n\n" +
                                        "Permission is hereby granted.",
                                url = "https://www.boost.org/LICENSE_1_0.txt",
                            ),
                        ),
                ),
                viewModel.uiState.value,
            )
        }

    @Test
    fun `a library without developers or a license text shows neither`() =
        runTest {
            val viewModel = viewModel(MATERIAL3.id)

            val content = viewModel.uiState.value as OpenSourceLibraryUiState.Content
            assertEquals(null, content.developers)
            assertEquals(null, content.licenses.first { it.id == "Apache-2.0" }.text)
        }

    @Test
    fun `an id that is not a Maven artifact is not shown as one`() =
        runTest {
            repository.libraries = listOf(TDLIB.copy(id = "google-sans-flex"))

            val viewModel = viewModel("google-sans-flex")

            assertEquals(false, (viewModel.uiState.value as OpenSourceLibraryUiState.Content).isMavenArtifact)
        }

    @Test
    fun `a library this build does not have shows not found`() =
        runTest {
            val viewModel = viewModel("com.example:gone")

            assertEquals(OpenSourceLibraryUiState.Error(DomainError.NotFound), viewModel.uiState.value)
        }

    @Test
    fun `retry reads the library again after it failed`() =
        runTest {
            repository.error = DomainError.Unknown(0, "unreadable")
            val viewModel = viewModel()
            repository.error = null

            viewModel.onEvent(OpenSourceLibraryEvent.Retry)
            runCurrent()

            assertEquals("TDLib", (viewModel.uiState.value as OpenSourceLibraryUiState.Content).name)
        }

    @Test
    fun `the website row opens the library's website`() =
        runTest {
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(OpenSourceLibraryEvent.OpenWebsite)

                assertEquals(OpenSourceLibraryEffect.OpenUrl("https://github.com/tdlib/td"), awaitItem())
            }
        }

    @Test
    fun `a license's link opens it online`() =
        runTest {
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(OpenSourceLibraryEvent.OpenLicense("BSL-1.0"))

                assertEquals(OpenSourceLibraryEffect.OpenUrl("https://www.boost.org/LICENSE_1_0.txt"), awaitItem())
            }
        }
}
