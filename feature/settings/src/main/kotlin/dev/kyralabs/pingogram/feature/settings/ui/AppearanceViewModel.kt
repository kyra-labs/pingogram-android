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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Appearance page shows (`REQUIREMENTS.md` FR-S2): nothing until the settings are read, then the settings. */
@Immutable
sealed interface AppearanceUiState {
    data object Loading : AppearanceUiState

    data class Content(
        val settings: AppearanceSettings,
    ) : AppearanceUiState
}

/** What the user changes on the Appearance page, handled by [AppearanceViewModel.onEvent]. */
sealed interface AppearanceEvent {
    data class SetThemeMode(
        val themeMode: ThemeMode,
    ) : AppearanceEvent

    data class SetDynamicColor(
        val enabled: Boolean,
    ) : AppearanceEvent

    data class SetAmoled(
        val enabled: Boolean,
    ) : AppearanceEvent

    data class SetContrast(
        val contrast: Contrast,
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
 * cannot be saved changes neither. Nothing to restore after process death: the settings are saved.
 */
@HiltViewModel
class AppearanceViewModel
    @Inject
    constructor(
        private val appearanceRepository: AppearanceRepository,
    ) : ViewModel() {
        val uiState: StateFlow<AppearanceUiState> =
            appearanceRepository.appearance
                .map<AppearanceSettings, AppearanceUiState> { AppearanceUiState.Content(it) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceUiState.Loading)

        private val effectChannel = Channel<AppearanceEffect>(Channel.BUFFERED)
        val effects: Flow<AppearanceEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: AppearanceEvent) {
            viewModelScope.launch {
                val saved =
                    when (event) {
                        is AppearanceEvent.SetThemeMode -> appearanceRepository.setThemeMode(event.themeMode)
                        is AppearanceEvent.SetDynamicColor -> appearanceRepository.setDynamicColor(event.enabled)
                        is AppearanceEvent.SetAmoled -> appearanceRepository.setAmoled(event.enabled)
                        is AppearanceEvent.SetContrast -> appearanceRepository.setContrast(event.contrast)
                    }
                saved.onFailure { effectChannel.trySend(AppearanceEffect.ShowError(it)) }
            }
        }
    }
