package dev.kyralabs.pingogram.feature.settings.ui.transcription

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.TranscriptionLanguages
import dev.kyralabs.pingogram.core.domain.repository.TranscriptionSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.VoiceTranscriber
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The page of voice message transcription (`docs/DESIGN_SYSTEM.md` §6 "Voice message transcription", the owner's board
 * 28d): whether the device can transcribe, "Transcribe voice messages automatically", and the speech languages of the
 * device's recognizer, which Android downloads and manages. The languages are asked of the recognizer once per page.
 */
@HiltViewModel
class TranscriptionSettingsViewModel
    @Inject
    constructor(
        private val settings: TranscriptionSettingsRepository,
        private val transcriber: VoiceTranscriber,
    ) : ViewModel() {
        private val effectChannel = Channel<TranscriptionSettingsEffect>(Channel.BUFFERED)
        val effects: Flow<TranscriptionSettingsEffect> = effectChannel.receiveAsFlow()

        private val languages: Flow<TranscriptionLanguages?> =
            flow {
                emit(null)
                if (transcriber.isAvailable) emit(transcriber.languages() ?: TranscriptionLanguages())
            }

        val uiState: StateFlow<TranscriptionSettingsUiState> =
            combine(settings.settings, languages) { settings, languages ->
                if (!transcriber.isAvailable) {
                    TranscriptionSettingsUiState.Unavailable
                } else {
                    TranscriptionSettingsUiState.Content(
                        automatic = settings.automatic,
                        languages = languages?.let(::languageRows),
                    )
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                TranscriptionSettingsUiState.Loading,
            )

        fun onEvent(event: TranscriptionSettingsEvent) {
            when (event) {
                is TranscriptionSettingsEvent.SetAutomatic -> {
                    viewModelScope.launch {
                        val saved = settings.setAutomatic(event.enabled)
                        if (saved is Outcome.Failure) {
                            effectChannel.trySend(
                                TranscriptionSettingsEffect.ShowError(saved.error),
                            )
                        }
                    }
                }

                TranscriptionSettingsEvent.OpenLanguageSettings -> {
                    effectChannel.trySend(TranscriptionSettingsEffect.OpenLanguageSettings)
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** The languages as rows: ready first, then those Android is downloading, then those it can download. */
private fun languageRows(languages: TranscriptionLanguages): ImmutableList<TranscriptionLanguage> =
    (
        languages.ready.map { TranscriptionLanguage(it, LanguageState.Ready) } +
            languages.downloading.map { TranscriptionLanguage(it, LanguageState.Downloading) } +
            languages.notDownloaded.map { TranscriptionLanguage(it, LanguageState.NotOnDevice) }
    ).distinctBy { it.tag }.toImmutableList()

/** What the transcription page shows. */
@Immutable
sealed interface TranscriptionSettingsUiState {
    /** Before the settings are read. */
    data object Loading : TranscriptionSettingsUiState

    /** Android before 13, or no on-device recognizer: the page says why, and offers nothing. */
    data object Unavailable : TranscriptionSettingsUiState

    /** The switch, and the recognizer's [languages] (null while it is asked). */
    data class Content(
        val automatic: Boolean,
        val languages: ImmutableList<TranscriptionLanguage>? = persistentListOf(),
    ) : TranscriptionSettingsUiState
}

/** A speech language of the recognizer, by its IETF BCP 47 [tag]. */
@Immutable
data class TranscriptionLanguage(
    val tag: String,
    val state: LanguageState,
)

/** Where a speech language stands on the device. */
enum class LanguageState { Ready, Downloading, NotOnDevice }

/** What the user does on the transcription page. */
sealed interface TranscriptionSettingsEvent {
    /** "Transcribe voice messages automatically" turned on or off. */
    data class SetAutomatic(
        val enabled: Boolean,
    ) : TranscriptionSettingsEvent

    /** "Android language settings". */
    data object OpenLanguageSettings : TranscriptionSettingsEvent
}

/** One-off effects of the transcription page. */
sealed interface TranscriptionSettingsEffect {
    /** The setting could not be saved: a snackbar. */
    data class ShowError(
        val error: DomainError,
    ) : TranscriptionSettingsEffect

    /** Opens Android's speech language settings. */
    data object OpenLanguageSettings : TranscriptionSettingsEffect
}
