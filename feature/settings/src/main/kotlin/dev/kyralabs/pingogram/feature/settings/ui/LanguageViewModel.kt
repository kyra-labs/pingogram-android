package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.repository.AppLanguageRepository
import kotlinx.collections.immutable.ImmutableList
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
import java.util.Locale
import javax.inject.Inject

/** What the Language page shows (`REQUIREMENTS.md` FR-S1): nothing until the languages are read, then the choice. */
@Immutable
sealed interface LanguageUiState {
    data object Loading : LanguageUiState

    /**
     * The [languages] Pingogram ships (IETF BCP 47 tags), and the [selected] one, or null while the app follows the
     * system's language.
     */
    data class Content(
        val languages: ImmutableList<String>,
        val selected: String?,
    ) : LanguageUiState
}

/** What the user changes on the Language page, handled by [LanguageViewModel.onEvent]. */
sealed interface LanguageEvent {
    /** Makes [languageTag] the app's language, or the system's with null. */
    data class Select(
        val languageTag: String?,
    ) : LanguageEvent
}

/** One-off side effects of [LanguageViewModel], handled once by its route. */
sealed interface LanguageEffect {
    /** The language could not be saved, shown in a snackbar (errors are never dialogs). */
    data class ShowError(
        val error: DomainError,
    ) : LanguageEffect
}

/**
 * The Language page (`REQUIREMENTS.md` FR-S1, §6.4): the languages Pingogram ships and the one chosen, and choosing.
 * The screens show a new language once they are created again, which Android (13+) or `MainActivity` does at once;
 * this view model outlives that. The choice is read again each time the page shows, as Android's settings can change it
 * meanwhile (Android 13+). A language Android's settings chose with a region (English, United Kingdom) selects the
 * shipped language it is a variant of. Nothing to restore after process death: the choice is saved.
 */
@HiltViewModel
class LanguageViewModel
    @Inject
    constructor(
        private val appLanguageRepository: AppLanguageRepository,
    ) : ViewModel() {
        val uiState: StateFlow<LanguageUiState> =
            combine(
                flow { emit(appLanguageRepository.supportedLanguages()) },
                appLanguageRepository.language,
            ) { languages, language ->
                LanguageUiState.Content(languages.toImmutableList(), shippedLanguageOf(language, languages))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), LanguageUiState.Loading)

        private val effectChannel = Channel<LanguageEffect>(Channel.BUFFERED)
        val effects: Flow<LanguageEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: LanguageEvent) {
            when (event) {
                is LanguageEvent.Select -> {
                    viewModelScope.launch {
                        appLanguageRepository
                            .setLanguage(event.languageTag)
                            .onFailure { effectChannel.trySend(LanguageEffect.ShowError(it)) }
                    }
                }
            }
        }
    }

/** The language of [languages] that [languageTag] is, or is a variant of ("en-GB" of "en"); null for none. */
internal fun shippedLanguageOf(
    languageTag: String?,
    languages: List<String>,
): String? {
    if (languageTag == null || languageTag in languages) return languageTag
    val language = Locale.forLanguageTag(languageTag).language
    return languages.firstOrNull { Locale.forLanguageTag(it).language == language }
}
