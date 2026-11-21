package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.ui.wallpaper.ChatWallpaperLayer
import dev.kyralabs.pingogram.core.ui.wallpaper.PhotoPreviewWallpaperLayer

/**
 * A preview's wallpaper (the wallpaper page's and Chat appearance's): the one picked before it is applied (a photo
 * from the phone as it will look, blurred if asked), else [set], else the theme-color one; dimmed in dark theme by
 * [dimming] percent.
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
