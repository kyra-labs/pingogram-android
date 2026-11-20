package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.AccountWallpapers
import dev.kyralabs.pingogram.core.domain.model.BubbleCorners
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.domain.repository.PickedPhotoRepository
import dev.kyralabs.pingogram.core.domain.repository.WallpaperRepository
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.WallpaperEvent
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.WallpaperPicker
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.WallpaperPickerState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Chat appearance page shows: nothing until the settings are read, then the settings. */
@Immutable
sealed interface ChatAppearanceUiState {
    data object Loading : ChatAppearanceUiState

    /**
     * The saved [appearance], the account's wallpapers ([account]: Telegram's or a photo, for light and dark theme)
     * and the wallpaper [picker].
     */
    data class Content(
        val appearance: ChatAppearance,
        val account: AccountWallpapers = AccountWallpapers(),
        val picker: WallpaperPickerState = WallpaperPickerState(),
    ) : ChatAppearanceUiState
}

/** One-off side effects of [ChatAppearanceViewModel], handled once by its route. */
sealed interface ChatAppearanceEffect {
    /** A setting could not be saved, or a wallpaper set or loaded, shown in a snackbar (errors are never dialogs). */
    data class ShowError(
        val error: DomainError,
    ) : ChatAppearanceEffect
}

/**
 * The Chat appearance page (`REQUIREMENTS.md` FR-S1): the saved [ChatAppearance], and saving what the user picks. The
 * page shows what is saved, which the theme ([dev.kyralabs.pingogram.core.ui.theme.ChatStyle]) and so the page's own
 * preview follow too: a change shows at once, and one that cannot be saved shows nowhere.
 *
 * The wallpaper is the account's for light and dark theme (owner decision 2026-11-20, [WallpaperRepository]): one of
 * Telegram's or a photo, picked and previewed before it is applied ([WallpaperPicker]); a theme-color one shows where
 * the account has none, so picking one, or "Remove", removes the account's for the theme picked for. The pick does not
 * survive process death; the copy of a picked photo is deleted with the view model.
 */
@HiltViewModel
class ChatAppearanceViewModel
    @Inject
    constructor(
        private val appearanceRepository: AppearanceRepository,
        private val wallpaperRepository: WallpaperRepository,
        photos: PickedPhotoRepository,
    ) : ViewModel() {
        private val effectChannel = Channel<ChatAppearanceEffect>(Channel.BUFFERED)
        val effects: Flow<ChatAppearanceEffect> = effectChannel.receiveAsFlow()

        private val picker = WallpaperPicker(viewModelScope, wallpaperRepository, photos, ::showError)

        val uiState: StateFlow<ChatAppearanceUiState> =
            combine(
                appearanceRepository.appearance,
                wallpaperRepository.defaults,
                picker.state,
            ) { settings, account, pick ->
                ChatAppearanceUiState.Content(settings.chat, account, pick)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatAppearanceUiState.Loading)

        /** Saves message text's [scale], put on its nearest step. */
        fun setTextScale(scale: Float) =
            save { appearanceRepository.setChatTextScale(ChatAppearance.textScaleOf(scale)) }

        fun setBubbleCorners(corners: BubbleCorners) = save { appearanceRepository.setBubbleCorners(corners) }

        /**
         * Saves the theme-color [wallpaper], which shows where the account has none: the account's for the theme picked
         * for is removed, and a pick dropped.
         */
        fun setWallpaper(wallpaper: ChatWallpaper) {
            picker.cancel()
            save { appearanceRepository.setChatWallpaper(wallpaper) }
            removeAccountWallpaper()
        }

        /** Saves how much the account's wallpaper dims in dark theme, in percent. */
        fun setWallpaperDimming(percent: Int) = save { appearanceRepository.setWallpaperDimming(percent) }

        /** What the user does in the "Wallpaper" group with the account's wallpapers. */
        fun onWallpaperEvent(event: WallpaperEvent) =
            when (event) {
                is WallpaperEvent.ShowTheme -> picker.showTheme(event.dark)
                WallpaperEvent.Retry -> picker.retry()
                is WallpaperEvent.Pick -> picker.pick(event.wallpaper)
                is WallpaperEvent.PickPhoto -> picker.pickPhoto(event.uri)
                is WallpaperEvent.SetBlurred -> picker.setBlurred(event.blurred)
                WallpaperEvent.Cancel -> picker.cancel()
                is WallpaperEvent.Apply -> picker.apply(event.patternFill, wallpaperRepository::setDefault)
                WallpaperEvent.Remove -> removeAccountWallpaper()
            }

        /** Removes the account's wallpaper for the theme picked for, if it has one: the theme-color one shows again. */
        private fun removeAccountWallpaper() {
            val dark = picker.state.value.forDarkTheme ?: return
            if (wallpaperRepository.defaults.value.forTheme(dark) == null) return
            save { wallpaperRepository.removeDefault(dark) }
        }

        override fun onCleared() = picker.release()

        private fun save(write: suspend () -> Outcome<Unit>) {
            viewModelScope.launch { write().onFailure(::showError) }
        }

        private fun showError(error: DomainError) {
            effectChannel.trySend(ChatAppearanceEffect.ShowError(error))
        }
    }
