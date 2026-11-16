package dev.kyralabs.pingogram.feature.settings.ui.transcription

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.TranscriptionLanguages
import dev.kyralabs.pingogram.core.testing.FakeTranscriptionSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeVoiceTranscriber
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The page of voice message transcription: the switch, the recognizer's languages, and a device without one. */
@OptIn(ExperimentalCoroutinesApi::class)
class TranscriptionSettingsViewModelTest {
    private val settings = FakeTranscriptionSettingsRepository()
    private val transcriber =
        FakeVoiceTranscriber(
            languages =
                TranscriptionLanguages(
                    ready = listOf("en-IN", "hi-IN"),
                    downloading = listOf("de-DE"),
                    notDownloaded = listOf("ta-IN", "en-IN"),
                ),
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): TranscriptionSettingsViewModel {
        val viewModel = TranscriptionSettingsViewModel(settings, transcriber)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun `shows the switch and the languages, ready first`() =
        runTest {
            val state = viewModel().uiState.value

            assertEquals(
                TranscriptionSettingsUiState.Content(
                    automatic = false,
                    languages =
                        persistentListOf(
                            TranscriptionLanguage("en-IN", LanguageState.Ready),
                            TranscriptionLanguage("hi-IN", LanguageState.Ready),
                            TranscriptionLanguage("de-DE", LanguageState.Downloading),
                            TranscriptionLanguage("ta-IN", LanguageState.NotOnDevice),
                        ),
                ),
                state,
            )
        }

    @Test
    fun `turning automatic transcription on saves it`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(TranscriptionSettingsEvent.SetAutomatic(true))
            runCurrent()

            assertTrue(settings.settings.value.automatic)
            assertEquals(true, (viewModel.uiState.value as TranscriptionSettingsUiState.Content).automatic)
        }

    @Test
    fun `a setting that cannot be saved is a snackbar`() =
        runTest {
            settings.failure = DomainError.Unknown(0, "disk full")
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(TranscriptionSettingsEvent.SetAutomatic(true))
                runCurrent()
                assertEquals(TranscriptionSettingsEffect.ShowError(DomainError.Unknown(0, "disk full")), awaitItem())
            }
        }

    @Test
    fun `the language settings open through an effect`() =
        runTest {
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(TranscriptionSettingsEvent.OpenLanguageSettings)
                assertEquals(TranscriptionSettingsEffect.OpenLanguageSettings, awaitItem())
            }
        }

    @Test
    fun `a device that cannot transcribe says so`() =
        runTest {
            transcriber.isAvailable = false

            assertEquals(TranscriptionSettingsUiState.Unavailable, viewModel().uiState.value)
        }
}
