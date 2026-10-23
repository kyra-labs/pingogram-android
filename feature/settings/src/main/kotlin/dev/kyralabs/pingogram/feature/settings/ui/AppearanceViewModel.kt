package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.domain.repository.ComposerSettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Appearance page shows (`REQUIREMENTS.md` FR-S2): nothing until the settings are read, then the settings. */
@Immutable
sealed interface AppearanceUiState {
    data object Loading : AppearanceUiState

    /** The [settings], and whether Ctrl+Enter sends messages from a hardware keyboard ([sendWithCtrlEnter]). */
    data class Content(
        val settings: AppearanceSettings,
        val sendWithCtrlEnter: Boolean = false,
    ) : AppearanceUiState
}

/** What the user changes on the Appearance page, handled by [AppearanceViewModel.onEvent]. */
sealed interface AppearanceEvent {
    data class SetThemeMode(
        val themeMode: ThemeMode,
    ) : AppearanceEvent

    data class SetAmoled(
        val enabled: Boolean,
    ) : AppearanceEvent

    data class SetContrast(
        val contrast: Contrast,
    ) : AppearanceEvent

    /** "Send with Ctrl+Enter" turned on or off. */
    data class SetSendWithCtrlEnter(
        val enabled: Boolean,
    ) : AppearanceEvent
}

/** One-off side effects of [AppearanceViewModel], handled once by its route. */
sealed interface AppearanceEffect {
    /** A setting could not be saved, shown in a snackbar (errors are never dialogs). */
    data class ShowError(
        val error: DomainError,
    ) : AppearanceEffect
}

/**
 * The Appearance page (`REQUIREMENTS.md` FR-S2): the saved settings, and saving each change. The page shows what is
 * saved, which the app's theme follows too, so a change shows on the page and in the theme at once, and a change that
 * cannot be saved changes neither. Nothing to restore after process death: the settings are saved. Its "Chats" group
 * also holds the composer's send key ([ComposerSettingsRepository]).
 */
@HiltViewModel
class AppearanceViewModel
    @Inject
    constructor(
        private val appearanceRepository: AppearanceRepository,
        private val composerSettingsRepository: ComposerSettingsRepository,
    ) : ViewModel() {
        val uiState: StateFlow<AppearanceUiState> =
            combine<AppearanceSettings, Boolean, AppearanceUiState>(
                appearanceRepository.appearance,
                composerSettingsRepository.sendWithCtrlEnter,
            ) { settings, ctrlEnter -> AppearanceUiState.Content(settings, ctrlEnter) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceUiState.Loading)

        private val effectChannel = Channel<AppearanceEffect>(Channel.BUFFERED)
        val effects: Flow<AppearanceEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: AppearanceEvent) {
            viewModelScope.launch {
                val saved =
                    when (event) {
                        is AppearanceEvent.SetThemeMode -> {
                            appearanceRepository.setThemeMode(event.themeMode)
                        }

                        is AppearanceEvent.SetAmoled -> {
                            appearanceRepository.setAmoled(event.enabled)
                        }

                        is AppearanceEvent.SetContrast -> {
                            appearanceRepository.setContrast(event.contrast)
                        }

                        is AppearanceEvent.SetSendWithCtrlEnter -> {
                            composerSettingsRepository.setSendWithCtrlEnter(event.enabled)
                        }
                    }
                saved.onFailure { effectChannel.trySend(AppearanceEffect.ShowError(it)) }
            }
        }
    }
