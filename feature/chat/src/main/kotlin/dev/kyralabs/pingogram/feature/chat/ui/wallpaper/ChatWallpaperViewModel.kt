package dev.kyralabs.pingogram.feature.chat.ui.wallpaper

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.AccountWallpapers
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaperOverride
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.domain.repository.WallpaperRepository
import dev.kyralabs.pingogram.core.domain.repository.WallpaperSeedRepository
import dev.kyralabs.pingogram.core.ui.theme.fillSeed
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The wallpaper of chat [chatId]'s conversation (owner decision 2026-11-20, [WallpaperRepository]): its own, which
 * either person in a private chat may set, else the account's, with the dark theme's dimming Chat appearance keeps
 * for the account's ([AppearanceRepository]). A view model of its own beside `ChatViewModel`, as the
 * connection's is: only the wallpaper layer reads it, so a change composes nothing else. Collected only while the
 * screen is ([STOP_TIMEOUT_MILLIS] after it leaves, as rotating keeps it).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ChatWallpaperViewModel.Factory::class)
class ChatWallpaperViewModel
    @AssistedInject
    constructor(
        @Assisted chatId: Long,
        repository: WallpaperRepository,
        appearance: AppearanceRepository,
        private val seedRepository: WallpaperSeedRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(chatId: Long): ChatWallpaperViewModel
        }

        private val own = repository.chatWallpaper(chatId)

        /** The kept seeds of the photos this conversation may show: the account's for either theme, and its own. */
        private val seeds: Flow<ImmutableMap<Long, Int>> =
            combine(repository.defaults, own) { account, own ->
                listOfNotNull(
                    account.light,
                    account.dark,
                    own?.wallpaper,
                ).filterIsInstance<Wallpaper.Photo>().map { it.id }
            }.distinctUntilChanged()
                .flatMapLatest { ids ->
                    if (ids.isEmpty()) {
                        flowOf(persistentMapOf())
                    } else {
                        combine(ids.distinct().map { id -> seedRepository.seed(id).map { id to it } }) { seeds ->
                            seeds.mapNotNull { (id, seed) -> seed?.let { id to it } }.toMap().toImmutableMap()
                        }
                    }
                }

        val wallpaper: StateFlow<ConversationWallpaper> =
            combine(
                repository.defaults,
                own,
                appearance.appearance.map { it.chat.wallpaperDimming }.distinctUntilChanged(),
                seeds,
                ::ConversationWallpaper,
            ).stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                ConversationWallpaper(repository.defaults.value),
            )

        /** Keeps the [seed] found for photo [wallpaperId]; if it cannot be kept, it is found again next time. */
        fun keepSeed(
            wallpaperId: Long,
            seed: Int,
        ) {
            viewModelScope.launch { seedRepository.setSeed(wallpaperId, seed) }
        }

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
    val accountDimming: Int = ChatAppearance.DEFAULT_WALLPAPER_DIMMING,
    val seeds: ImmutableMap<Long, Int> = persistentMapOf(),
) {
    /** What shows in [dark] theme, its dimming and its seed; a null wallpaper for the theme-color one. */
    fun forTheme(dark: Boolean): ShownWallpaper {
        val wallpaper = own?.wallpaper ?: account.forTheme(dark)
        val dimming = own?.darkThemeDimming ?: accountDimming
        return ShownWallpaper(wallpaper, dimming, seedOf(wallpaper))
    }

    /**
     * The seed the conversation's colors come from (`docs/adr/0041-chat-colors-from-the-wallpaper.md`): a photo's
     * kept one ([seeds], null until it is found), a fill's from its colors; none for a pattern, drawn in the theme's
     * colors, nor for no wallpaper, so those keep the app's colors.
     */
    private fun seedOf(wallpaper: Wallpaper?): Int? =
        when (wallpaper) {
            is Wallpaper.Photo -> seeds[wallpaper.id]
            is Wallpaper.Fill -> fillSeed(wallpaper.colors.map { it or OPAQUE })
            is Wallpaper.Pattern, null -> null
        }

    private companion object {
        const val OPAQUE = 0xFF shl 24
    }
}

/** A wallpaper to draw, how much it dims in dark theme, and the seed of the conversation's colors (null: the app's). */
data class ShownWallpaper(
    val wallpaper: Wallpaper?,
    val darkThemeDimming: Int,
    val seed: Int? = null,
)
