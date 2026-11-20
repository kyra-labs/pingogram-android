package dev.kyralabs.pingogram.feature.chat.ui.wallpaper

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.AccountWallpapers
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaperOverride
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.domain.repository.WallpaperRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The wallpaper of chat [chatId]'s conversation (owner decision 2026-11-20, [WallpaperRepository]): its own, which
 * either person in a private chat may set, else the account's, with the dark theme's dimming Chat appearance keeps
 * for the account's ([AppearanceRepository]). A view model of its own beside `ChatViewModel`, as the
 * connection's is: only the wallpaper layer reads it, so a change composes nothing else. Collected only while the
 * screen is ([STOP_TIMEOUT_MILLIS] after it leaves, as rotating keeps it).
 */
@HiltViewModel(assistedFactory = ChatWallpaperViewModel.Factory::class)
class ChatWallpaperViewModel
    @AssistedInject
    constructor(
        @Assisted chatId: Long,
        repository: WallpaperRepository,
        appearance: AppearanceRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(chatId: Long): ChatWallpaperViewModel
        }

        val wallpaper: StateFlow<ConversationWallpaper> =
            combine(
                repository.defaults,
                repository.chatWallpaper(chatId),
                appearance.appearance.map { it.chat.wallpaperDimming }.distinctUntilChanged(),
                ::ConversationWallpaper,
            ).stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                ConversationWallpaper(repository.defaults.value),
            )

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/**
 * The account's wallpapers, dimmed by [accountDimming] percent in dark theme (kept on the device, as TDLib keeps none
 * for them), and the chat's [own], which wins over them in both themes with its own dimming.
 */
@Immutable
data class ConversationWallpaper(
    val account: AccountWallpapers = AccountWallpapers(),
    val own: ChatWallpaperOverride? = null,
    val accountDimming: Int = 0,
) {
    /** What shows in [dark] theme and its dimming; a null wallpaper for the theme-color one. */
    fun forTheme(dark: Boolean): ShownWallpaper =
        own?.let { ShownWallpaper(it.wallpaper, it.darkThemeDimming) }
            ?: ShownWallpaper(account.forTheme(dark), accountDimming)
}

/** A wallpaper to draw and how much it dims in dark theme. */
data class ShownWallpaper(
    val wallpaper: Wallpaper?,
    val darkThemeDimming: Int,
)
