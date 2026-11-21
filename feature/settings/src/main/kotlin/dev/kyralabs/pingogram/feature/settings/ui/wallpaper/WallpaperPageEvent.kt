package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper

/** What the user does on the wallpaper page. Nothing is set until [Apply]. */
sealed interface WallpaperPageEvent {
    /** Picking one of Telegram's wallpapers or a photo ([WallpaperPicker]). */
    data class Picker(
        val event: WallpaperEvent,
    ) : WallpaperPageEvent

    /** Previews a theme-color [wallpaper] in place of the account's; the account's page only. */
    data class PickThemeColor(
        val wallpaper: ChatWallpaper,
    ) : WallpaperPageEvent

    /** Previews how much the wallpaper dims in dark theme, in percent. */
    data class SetDimming(
        val percent: Int,
    ) : WallpaperPageEvent

    /** Previews the default: no wallpaper, the theme's colors, 30% dimming. */
    data object Reset : WallpaperPageEvent

    /**
     * Sets what is previewed, a pattern on [patternFill] (the theme's colors, 24-bit RGB) for the other apps; a chat's
     * for both people with [forBoth], which needs Telegram Premium, else for the user alone.
     */
    data class Apply(
        val patternFill: List<Int>,
        val forBoth: Boolean = false,
    ) : WallpaperPageEvent
}

/** One-off side effects of [WallpaperPageViewModel]: what failed, for a snackbar. */
sealed interface WallpaperPageEffect {
    data class ShowError(
        val error: DomainError,
    ) : WallpaperPageEffect
}
