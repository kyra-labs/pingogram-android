package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice

/**
 * What the wallpaper page shows (ADR 0041): what is set ([saved]), what is picked and not applied yet ([picker],
 * [draft]), for a chat its title and photo ([chat], null until known) and whether Telegram Premium lets the user set it
 * for both people ([canSetForBoth]), and the seed the preview's colors come from ([seed], null for the app's).
 */
@Immutable
data class WallpaperPageUiState(
    val isChat: Boolean,
    val chat: WallpaperPageChat? = null,
    val picker: WallpaperPickerState = WallpaperPickerState(),
    val saved: SavedWallpaper = SavedWallpaper(),
    val draft: WallpaperDraft = WallpaperDraft(),
    val canSetForBoth: Boolean = false,
    val seed: Int? = null,
) {
    /** The dimming in dark theme the preview shows and Apply sets, in percent. */
    val dimming: Int
        get() = draft.dimming ?: if (draft.isReset) ChatAppearance.DEFAULT_WALLPAPER_DIMMING else saved.dimming

    /** The Telegram wallpaper shown where nothing is picked: none once a reset or a theme color is previewed. */
    val shownWallpaper: Wallpaper?
        get() = if (draft.isReset || draft.themeColor != null) null else saved.wallpaper

    /** The Telegram wallpaper the preview shows, picked or set, a photo blurred as picked; null for a new photo. */
    val previewedWallpaper: Wallpaper?
        get() =
            when (val pending = picker.pending) {
                is WallpaperChoice.Existing -> pending.shown()
                is WallpaperChoice.NewPhoto -> null
                null -> shownWallpaper
            }

    /** The photo the preview shows, whose seed its colors come from; null for a fill, a pattern or none. */
    val seedPhoto: SeedPhoto?
        get() =
            when (val pending = picker.pending) {
                is WallpaperChoice.NewPhoto -> SeedPhoto.Picked(pending.path)
                else -> (previewedWallpaper as? Wallpaper.Photo)?.let { SeedPhoto.OnTelegram(it.id) }
            }

    /** The theme-color wallpaper shown where no Telegram wallpaper is (the account's page). */
    val shownThemeColor: ChatWallpaper
        get() = if (draft.isReset) ChatWallpaper.None else draft.themeColor ?: saved.themeColor

    /** Something differs from what is set, so Apply has something to set. */
    val hasChanges: Boolean
        get() {
            val themeColor = draft.themeColor
            val resets = draft.isReset && !saved.isDefault
            val recolors = themeColor != null && (themeColor != saved.themeColor || saved.wallpaper != null)
            val dims = dimming != saved.dimming && (!isChat || (saved.wallpaper != null && !draft.isReset))
            return picker.pending != null || resets || recolors || dims
        }

    /** A photo is being prepared or something set, so nothing else may be applied meanwhile. */
    val isWorking: Boolean
        get() = picker.isWorking || draft.isApplying

    val canApply: Boolean
        get() = hasChanges && !isWorking

    /** "Reset to default" previews the default, unless it already shows. */
    val canReset: Boolean
        get() = !draft.isReset && (!saved.isDefault || hasChanges)
}

/** A chat's title and photo, for "Wallpaper for <name>" and the preview's top bar; [id] colors its placeholder. */
@Immutable
data class WallpaperPageChat(
    val id: Long,
    val title: String,
    val photo: ProfilePhoto?,
)

/**
 * What is set: the account's wallpaper for the theme picked for, or the chat's own ([wallpaper], null for none), the
 * account's theme-color wallpaper ([themeColor]; none for a chat) and the dimming in dark theme.
 */
@Immutable
data class SavedWallpaper(
    val wallpaper: Wallpaper? = null,
    val themeColor: ChatWallpaper = ChatWallpaper.None,
    val dimming: Int = ChatAppearance.DEFAULT_WALLPAPER_DIMMING,
) {
    val isDefault: Boolean
        get() =
            wallpaper == null && themeColor == ChatWallpaper.None &&
                dimming == ChatAppearance.DEFAULT_WALLPAPER_DIMMING
}

/**
 * What the page changes besides the picker's pick: a theme-color wallpaper picked ([themeColor]), the dimming dragged
 * to ([dimming]), or "Reset to default" previewed ([isReset]); [isApplying] while Apply sets them.
 */
@Immutable
data class WallpaperDraft(
    val themeColor: ChatWallpaper? = null,
    val dimming: Int? = null,
    val isReset: Boolean = false,
    val isApplying: Boolean = false,
)
