package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.SendKeys
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.domain.repository.ComposerSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.PlaybackSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
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

    /**
     * The [settings], which Enter sends the composer's messages ([sendKeys]) and how voice messages play at the ear
     * ([raiseToListen]).
     */
    data class Content(
        val settings: AppearanceSettings,
        val sendKeys: SendKeys = SendKeys(),
        val raiseToListen: RaiseToListenSettings = RaiseToListenSettings(),
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

    /** "Enter sends" turned on or off for a hardware or the on-screen keyboard. */
    data class SetSendKeys(
        val keys: SendKeys,
    ) : AppearanceEvent

    /** "Raise to listen" turned on or off. */
    data class SetRaiseToListen(
        val enabled: Boolean,
    ) : AppearanceEvent

    /** "Pause when lowered" turned on or off. */
    data class SetPauseWhenLowered(
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
 * also holds the composer's send key ([ComposerSettingsRepository]) and raise to listen ([PlaybackSettingsRepository]).
 */
@HiltViewModel
class AppearanceViewModel
    @Inject
    constructor(
        private val appearanceRepository: AppearanceRepository,
        private val composerSettingsRepository: ComposerSettingsRepository,
        private val playbackSettingsRepository: PlaybackSettingsRepository,
    ) : ViewModel() {
        val uiState: StateFlow<AppearanceUiState> =
            combine<AppearanceSettings, SendKeys, RaiseToListenSettings, AppearanceUiState>(
                appearanceRepository.appearance,
                composerSettingsRepository.sendKeys,
                playbackSettingsRepository.raiseToListen,
            ) { settings, keys, raise -> AppearanceUiState.Content(settings, keys, raise) }
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

                        is AppearanceEvent.SetSendKeys -> {
                            composerSettingsRepository.setSendKeys(event.keys)
                        }

                        is AppearanceEvent.SetRaiseToListen -> {
                            playbackSettingsRepository.setRaiseToListen(event.enabled)
                        }

                        is AppearanceEvent.SetPauseWhenLowered -> {
                            playbackSettingsRepository.setPauseWhenLowered(event.enabled)
                        }
                    }
                saved.onFailure { effectChannel.trySend(AppearanceEffect.ShowError(it)) }
            }
        }
    }
