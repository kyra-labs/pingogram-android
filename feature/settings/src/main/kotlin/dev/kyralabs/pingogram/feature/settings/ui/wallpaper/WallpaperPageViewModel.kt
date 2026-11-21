package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.PickedPhotoRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
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
 * The wallpaper page (ADR 0041), for the account's wallpaper ([chatId] null) or chat [chatId]'s own. Everything picked
 * is previewed first, and Apply sets it: a Telegram wallpaper or a photo ([WallpaperPicker]), a theme-color wallpaper
 * (the account's), the dimming in dark theme, or "Reset to default". A chat's goes to the user alone or, with Telegram
 * Premium, to both people. A failure keeps the wallpaper there was and what was picked, with a snackbar. The preview
 * takes the colors of the wallpaper it shows ([WallpaperPageSeeds]), and Apply keeps a photo's seed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = WallpaperPageViewModel.Factory::class)
class WallpaperPageViewModel
    @AssistedInject
    constructor(
        @Assisted private val chatId: Long?,
        repositories: WallpaperRepositories,
        appearance: AppearanceRepository,
        chats: ChatRepository,
        users: UserRepository,
        photos: PickedPhotoRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(chatId: Long?): WallpaperPageViewModel
        }

        private val wallpapers = repositories.wallpapers

        private val effectChannel = Channel<WallpaperPageEffect>(Channel.BUFFERED)
        val effects: Flow<WallpaperPageEffect> = effectChannel.receiveAsFlow()

        private val picker = WallpaperPicker(viewModelScope, wallpapers, photos, ::showError)
        private val draft = MutableStateFlow(WallpaperDraft())
        private val applier = WallpaperApplier(chatId, wallpapers, appearance)
        private val seeds = WallpaperPageSeeds(repositories.seeds)

        private val saved: Flow<SavedWallpaper> =
            if (chatId == null) {
                combine(appearance.appearance, wallpapers.defaults, picker.state) { settings, account, pick ->
                    SavedWallpaper(
                        wallpaper = pick.forDarkTheme?.let(account::forTheme),
                        themeColor = settings.chat.wallpaper,
                        dimming = settings.chat.wallpaperDimming,
                    )
                }
            } else {
                wallpapers.chatWallpaper(chatId).map { own ->
                    SavedWallpaper(
                        wallpaper = own?.wallpaper,
                        dimming = own?.darkThemeDimming ?: ChatAppearance.DEFAULT_WALLPAPER_DIMMING,
                    )
                }
            }

        private val chat: Flow<WallpaperPageChat?> =
            chatId?.let { id ->
                chats.chat(id).map { it?.let { chat -> WallpaperPageChat(chat.id, chat.title, chat.photo) } }
            }
                ?: flowOf(null)

        private val premium: Flow<Boolean> =
            users.myUserId
                .flatMapLatest { id -> id?.let(users::user) ?: flowOf(null) }
                .map { it?.isPremium == true }
                .distinctUntilChanged()

        private val pageState: StateFlow<WallpaperPageUiState> =
            combine(saved, picker.state, draft, chat, premium) { set, pick, made, title, canBoth ->
                WallpaperPageUiState(
                    isChat = chatId != null,
                    chat = title,
                    picker = pick,
                    saved = set,
                    draft = made,
                    canSetForBoth = canBoth,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, WallpaperPageUiState(isChat = chatId != null))

        val uiState: StateFlow<WallpaperPageUiState> =
            seeds.on(pageState).stateIn(viewModelScope, SharingStarted.Eagerly, pageState.value)

        fun onEvent(event: WallpaperPageEvent) {
            when (event) {
                is WallpaperPageEvent.Picker -> onPickerEvent(event.event)
                is WallpaperPageEvent.PickThemeColor -> pickThemeColor(event.wallpaper)
                is WallpaperPageEvent.SetDimming -> draft.update { it.copy(dimming = event.percent) }
                WallpaperPageEvent.Reset -> reset()
                is WallpaperPageEvent.SeedFound -> seeds.found(event.photo, event.seed)
                is WallpaperPageEvent.Apply -> apply(event.patternFill, event.forBoth)
            }
        }

        private fun onPickerEvent(event: WallpaperEvent) {
            when (event) {
                is WallpaperEvent.ShowTheme -> {
                    if (event.dark != picker.state.value.forDarkTheme) draft.value = WallpaperDraft()
                    picker.showTheme(event.dark)
                }

                WallpaperEvent.Retry -> {
                    picker.retry()
                }

                is WallpaperEvent.Pick -> {
                    draft.update { it.copy(themeColor = null, isReset = false) }
                    picker.pick(event.wallpaper)
                }

                is WallpaperEvent.PickPhoto -> {
                    draft.update { it.copy(themeColor = null, isReset = false) }
                    picker.pickPhoto(event.uri, event.screenSidePx)
                }

                is WallpaperEvent.SetBlurred -> {
                    setBlurred(event.blurred)
                }
            }
        }

        /** Blurs the photo picked, or the photo set when nothing is picked, which picks it as it is set. */
        private fun setBlurred(blurred: Boolean) {
            val state = uiState.value
            if (state.picker.pending == null) {
                val photo = state.shownWallpaper as? Wallpaper.Photo ?: return
                draft.update { it.copy(isReset = false) }
                picker.pick(photo)
            }
            picker.setBlurred(blurred)
        }

        private fun pickThemeColor(wallpaper: ChatWallpaper) {
            if (chatId != null) return
            picker.cancel()
            draft.update { it.copy(themeColor = wallpaper, isReset = false) }
        }

        /** Previews the default: no wallpaper of the account's or the chat's own, no theme color, 30% dimming. */
        private fun reset() {
            picker.cancel()
            draft.value = WallpaperDraft(isReset = true)
        }

        private fun apply(
            patternFill: List<Int>,
            forBoth: Boolean,
        ) {
            val state = uiState.value
            if (!state.hasChanges || state.isWorking) return
            val onlyForSelf = !(forBoth && state.canSetForBoth)
            if (state.picker.pending != null) {
                // A photo's seed is kept for the background it became; a fill's comes from its colors, and a pattern
                // has none.
                val photoSeed = state.seed.takeIf { state.seedPhoto != null }
                picker.apply(patternFill) { choice, dark ->
                    applier.setPick(choice, dark, state, onlyForSelf).map { setId ->
                        draft.clear()
                        if (setId != null) seeds.keep(setId, photoSeed)
                    }
                }
            } else {
                commit { applier.applyDraft(state, patternFill, onlyForSelf) }
            }
        }

        private fun commit(change: suspend () -> Outcome<Unit>) {
            draft.update { it.copy(isApplying = true) }
            viewModelScope.launch {
                when (val done = change()) {
                    is Outcome.Success -> draft.clear()
                    is Outcome.Failure -> showError(done.error)
                }
                draft.update { it.copy(isApplying = false) }
            }
        }

        override fun onCleared() = picker.release()

        private fun showError(error: DomainError) {
            effectChannel.trySend(WallpaperPageEffect.ShowError(error))
        }

        private fun MutableStateFlow<WallpaperDraft>.clear() = update { WallpaperDraft(isApplying = it.isApplying) }
    }
