package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.domain.repository.WallpaperRepository

/**
 * Sets what the wallpaper page previews ([WallpaperPageViewModel]), for the account ([chatId] null) or chat [chatId]:
 * a pick with its dimming, or with nothing picked the reset, a theme color or the dimming alone. Each stops at the
 * first failure, which leaves the rest as it was.
 */
internal class WallpaperApplier(
    private val chatId: Long?,
    private val wallpapers: WallpaperRepository,
    private val appearance: AppearanceRepository,
) {
    /** Sets [choice] for [forDarkTheme] (the account's) or as the chat's own, with [state]'s dimming. */
    suspend fun setPick(
        choice: WallpaperChoice,
        forDarkTheme: Boolean,
        state: WallpaperPageUiState,
        onlyForSelf: Boolean,
    ): Outcome<Unit> =
        if (chatId == null) {
            // The dimming stays on the device, so it goes first: a failed upload then changes nothing on Telegram.
            setAccountDimming(state).then { wallpapers.setDefault(choice, forDarkTheme) }
        } else {
            wallpapers.setChatWallpaper(chatId, choice, state.dimming, onlyForSelf)
        }

    /** Sets [state]'s draft with nothing picked: the reset, a theme color, or the dimming alone. */
    suspend fun applyDraft(
        state: WallpaperPageUiState,
        patternFill: List<Int>,
        onlyForSelf: Boolean,
    ): Outcome<Unit> {
        val draft = state.draft
        val removesSet = draft.isReset || draft.themeColor != null
        val own = state.saved.wallpaper
        return when {
            chatId == null -> {
                val themeColor = if (draft.isReset) ChatWallpaper.None else draft.themeColor
                val recolor = themeColor?.takeIf { it != state.saved.themeColor }
                val dark = state.picker.forDarkTheme.takeIf { removesSet }
                setAccountDimming(state)
                    .then { recolor?.let { appearance.setChatWallpaper(it) } ?: Outcome.Success(Unit) }
                    .then { removeAccountWallpaper(dark) }
            }

            removesSet -> {
                wallpapers.removeChatWallpaper(chatId)
            }

            own == null -> {
                Outcome.Success(Unit)
            }

            else -> {
                val choice = WallpaperChoice.Existing(own, (own as? Wallpaper.Photo)?.isBlurred == true, patternFill)
                wallpapers.setChatWallpaper(chatId, choice, state.dimming, onlyForSelf)
            }
        }
    }

    private suspend fun setAccountDimming(state: WallpaperPageUiState): Outcome<Unit> =
        if (state.dimming != state.saved.dimming) {
            appearance.setWallpaperDimming(state.dimming)
        } else {
            Outcome.Success(Unit)
        }

    /** Removes the account's wallpaper for [dark] theme, if it has one; nothing for null. */
    private suspend fun removeAccountWallpaper(dark: Boolean?): Outcome<Unit> =
        if (dark != null && wallpapers.defaults.value.forTheme(dark) != null) {
            wallpapers.removeDefault(dark)
        } else {
            Outcome.Success(Unit)
        }
}

private inline fun Outcome<Unit>.then(next: () -> Outcome<Unit>): Outcome<Unit> =
    if (this is Outcome.Success) next() else this
