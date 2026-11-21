package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaperOverride
import dev.kyralabs.pingogram.core.domain.repository.PickedPhotoRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.domain.repository.WallpaperRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What a chat's own wallpaper page shows (owner decision 2026-11-20): the chat's wallpaper, the picker, whether it is
 * set for both people, which only Telegram Premium allows ([canSetForBoth]), and its dimming in dark theme.
 */
@Immutable
data class ChatOwnWallpaperUiState(
    val own: ChatWallpaperOverride? = null,
    val picker: WallpaperPickerState = WallpaperPickerState(),
    val canSetForBoth: Boolean = false,
    val forBoth: Boolean = false,
    val dimming: Int = ChatAppearance.DEFAULT_WALLPAPER_DIMMING,
)

/** What the user does on a chat's own wallpaper page, besides picking ([WallpaperEvent]). */
sealed interface ChatOwnWallpaperEvent {
    /** Sets the wallpaper for both people ([forBoth]) or for the user alone. */
    data class SetForBoth(
        val forBoth: Boolean,
    ) : ChatOwnWallpaperEvent

    /** How much the wallpaper dims in dark theme, in percent. */
    data class SetDimming(
        val percent: Int,
    ) : ChatOwnWallpaperEvent

    /** Removes the chat's own wallpaper: the account's shows in it again. */
    data object Reset : ChatOwnWallpaperEvent
}

/** One-off side effects of [ChatOwnWallpaperViewModel]: what failed, for a snackbar. */
sealed interface ChatOwnWallpaperEffect {
    data class ShowError(
        val error: DomainError,
    ) : ChatOwnWallpaperEffect
}

/**
 * Chat [chatId]'s own wallpaper (td_api.tl `setChatBackground`, [WallpaperRepository]): picked and previewed as the
 * account's are ([WallpaperPicker]), then set for the user alone or, with Telegram Premium, for both people, dimmed in
 * dark theme by its own amount; "Reset" removes it. A failure keeps the wallpaper there was, with a snackbar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ChatOwnWallpaperViewModel.Factory::class)
class ChatOwnWallpaperViewModel
    @AssistedInject
    constructor(
        @Assisted private val chatId: Long,
        private val wallpapers: WallpaperRepository,
        users: UserRepository,
        photos: PickedPhotoRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(chatId: Long): ChatOwnWallpaperViewModel
        }

        private val effectChannel = Channel<ChatOwnWallpaperEffect>(Channel.BUFFERED)
        val effects: Flow<ChatOwnWallpaperEffect> = effectChannel.receiveAsFlow()

        private val picker = WallpaperPicker(viewModelScope, wallpapers, photos, ::showError)

        /** The choices made on the page; null dimming follows the chat's own, else the default. */
        private val choices = MutableStateFlow(Choices())

        private val premium: Flow<Boolean> =
            users.myUserId
                .flatMapLatest { id -> id?.let(users::user) ?: flowOf(null) }
                .map { it?.isPremium == true }
                .distinctUntilChanged()

        val uiState: StateFlow<ChatOwnWallpaperUiState> =
            combine(wallpapers.chatWallpaper(chatId), picker.state, premium, choices) { own, pick, canBoth, made ->
                ChatOwnWallpaperUiState(
                    own = own,
                    picker = pick,
                    canSetForBoth = canBoth,
                    forBoth = canBoth && made.forBoth,
                    dimming = made.dimming ?: own?.darkThemeDimming ?: ChatAppearance.DEFAULT_WALLPAPER_DIMMING,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatOwnWallpaperUiState())

        /** Picking, as on Chat appearance; Apply sets the pick as the chat's own. */
        fun onWallpaperEvent(event: WallpaperEvent) =
            when (event) {
                is WallpaperEvent.ShowTheme -> picker.showTheme(event.dark)
                WallpaperEvent.Retry -> picker.retry()
                is WallpaperEvent.Pick -> picker.pick(event.wallpaper)
                is WallpaperEvent.PickPhoto -> picker.pickPhoto(event.uri, event.screenSidePx)
                is WallpaperEvent.SetBlurred -> picker.setBlurred(event.blurred)
                WallpaperEvent.Cancel -> picker.cancel()
                is WallpaperEvent.Apply -> apply(event.patternFill)
                WallpaperEvent.Remove -> reset()
            }

        fun onEvent(event: ChatOwnWallpaperEvent) {
            when (event) {
                is ChatOwnWallpaperEvent.SetForBoth -> choices.update { it.copy(forBoth = event.forBoth) }
                is ChatOwnWallpaperEvent.SetDimming -> choices.update { it.copy(dimming = event.percent) }
                ChatOwnWallpaperEvent.Reset -> reset()
            }
        }

        private fun apply(patternFill: List<Int>) {
            val state = uiState.value
            picker.apply(patternFill) { choice, _ ->
                wallpapers.setChatWallpaper(chatId, choice, state.dimming, onlyForSelf = !state.forBoth)
            }
        }

        private fun reset() {
            viewModelScope.launch { wallpapers.removeChatWallpaper(chatId).onFailure(::showError) }
        }

        override fun onCleared() = picker.release()

        private fun showError(error: DomainError) {
            effectChannel.trySend(ChatOwnWallpaperEffect.ShowError(error))
        }

        /** What the user chose on the page so far. */
        private data class Choices(
            val forBoth: Boolean = false,
            val dimming: Int? = null,
        )
    }
