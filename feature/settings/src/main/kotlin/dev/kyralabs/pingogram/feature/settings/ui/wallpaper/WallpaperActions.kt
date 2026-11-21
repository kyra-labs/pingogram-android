package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.ui.wallpaper.ChatWallpaperLayer
import dev.kyralabs.pingogram.core.ui.wallpaper.PhotoPreviewWallpaperLayer

// The account's wallpapers in Chat appearance (docs/DESIGN_SYSTEM.md §6 "Chat appearance").

/** What the "Wallpaper" group does with the account's wallpapers; each does nothing by default, as in previews. */
@Immutable
internal class WallpaperActions(
    val onTheme: (dark: Boolean) -> Unit = {},
    val onPick: (Wallpaper) -> Unit = {},
    val onPickPhoto: () -> Unit = {},
    val onApply: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBlurred: (Boolean) -> Unit = {},
    val onRemove: () -> Unit = {},
    val onDimming: (percent: Int) -> Unit = {},
    val onDimmingSettled: () -> Unit = {},
)

/**
 * The preview's wallpaper: the one picked before it is applied (a photo from the phone as it will look, blurred if
 * asked), else the account's for the theme picked for, else the theme-color one; dimmed as Chat appearance says.
 */
@Composable
internal fun PreviewWallpaper(
    pending: WallpaperChoice?,
    set: Wallpaper?,
    dimming: Int,
    modifier: Modifier = Modifier,
) {
    when (pending) {
        is WallpaperChoice.NewPhoto -> PhotoPreviewWallpaperLayer(pending.path, pending.isBlurred, dimming, modifier)
        is WallpaperChoice.Existing -> ChatWallpaperLayer(pending.shown(), dimming, modifier)
        null -> ChatWallpaperLayer(set, dimming, modifier)
    }
}

/** The picked wallpaper as it will be set: a photo blurred as picked. */
private fun WallpaperChoice.Existing.shown(): Wallpaper =
    (wallpaper as? Wallpaper.Photo)?.copy(isBlurred = isBlurred) ?: wallpaper
