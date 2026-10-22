package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
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

/** A color theme the Theme page offers (`docs/adr/0015-color-themes.md`). */
@Immutable
sealed interface ColorThemeOption {
    /** Material You from the wallpaper (Android 12+). */
    data object Wallpaper : ColorThemeOption

    /** One of the generated themes. */
    data class Palette(
        val palette: ThemePalette,
    ) : ColorThemeOption
}

/**
 * The color theme [this] settings show: the wallpaper's where dynamic color is on and [wallpaperAvailable], the
 * palette's otherwise (also before Android 12, where dynamic color is on by default but has no colors).
 */
internal fun AppearanceSettings.colorTheme(wallpaperAvailable: Boolean): ColorThemeOption =
    if (dynamicColor && wallpaperAvailable) ColorThemeOption.Wallpaper else ColorThemeOption.Palette(palette)

/** What the Theme page shows: nothing until the settings are read, then the settings. */
@Immutable
sealed interface ColorThemeUiState {
    data object Loading : ColorThemeUiState

    data class Content(
        val settings: AppearanceSettings,
    ) : ColorThemeUiState
}

/** One-off side effects of [ColorThemeViewModel], handled once by its route. */
sealed interface ColorThemeEffect {
    /** The theme could not be saved, shown in a snackbar (errors are never dialogs). */
    data class ShowError(
        val error: DomainError,
    ) : ColorThemeEffect
}

/**
 * The Theme page (`REQUIREMENTS.md` FR-S2, `docs/adr/0015-color-themes.md`): the saved settings, and saving the theme
 * the user picks. A generated theme turns dynamic color off with it, in one write ([AppearanceRepository.setPalette]);
 * the wallpaper's colors turn it back on and keep the last theme for where they have no colors (a medium or high
 * contrast before Android 14). The page shows what is saved, which the app's theme follows too, so a pick shows on the
 * page and in the whole app at once; one that cannot be saved changes neither. Nothing to restore after process death.
 */
@HiltViewModel
class ColorThemeViewModel
    @Inject
    constructor(
        private val appearanceRepository: AppearanceRepository,
    ) : ViewModel() {
        val uiState: StateFlow<ColorThemeUiState> =
            appearanceRepository.appearance
                .map<AppearanceSettings, ColorThemeUiState> { ColorThemeUiState.Content(it) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, ColorThemeUiState.Loading)

        private val effectChannel = Channel<ColorThemeEffect>(Channel.BUFFERED)
        val effects: Flow<ColorThemeEffect> = effectChannel.receiveAsFlow()

        /** Saves [option] as the color theme. */
        fun select(option: ColorThemeOption) {
            viewModelScope.launch {
                val saved =
                    when (option) {
                        ColorThemeOption.Wallpaper -> appearanceRepository.setDynamicColor(true)
                        is ColorThemeOption.Palette -> appearanceRepository.setPalette(option.palette)
                    }
                saved.onFailure { effectChannel.trySend(ColorThemeEffect.ShowError(it)) }
            }
        }
    }
