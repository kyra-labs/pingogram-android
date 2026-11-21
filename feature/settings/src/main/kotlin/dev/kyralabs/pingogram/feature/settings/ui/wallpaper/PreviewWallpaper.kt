package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.ui.wallpaper.ChatWallpaperLayer
import dev.kyralabs.pingogram.core.ui.wallpaper.PhotoPreviewWallpaperLayer
import dev.kyralabs.pingogram.core.ui.wallpaper.WallpaperFrame

/**
 * A preview's wallpaper (the wallpaper page's and Chat appearance's): the one picked before it is applied (a photo
 * from the phone as it will look, blurred if asked), else [set], else the theme-color one; dimmed in dark theme by
 * [dimming] percent. In a [frame] it draws its part of the frame's wallpaper; [blurred], the copy behind a top bar.
 */
@Composable
internal fun PreviewWallpaper(
    pending: WallpaperChoice?,
    set: Wallpaper?,
    dimming: Int,
    modifier: Modifier = Modifier,
    frame: WallpaperFrame? = null,
    blurred: Boolean = false,
) {
    when (pending) {
        is WallpaperChoice.NewPhoto -> {
            PhotoPreviewWallpaperLayer(pending.path, blurred || pending.isBlurred, dimming, modifier, frame)
        }

        is WallpaperChoice.Existing -> {
            ChatWallpaperLayer(pending.shown(), dimming, modifier, frame, blurred)
        }

        null -> {
            ChatWallpaperLayer(set, dimming, modifier, frame, blurred)
        }
    }
}

/** The picked wallpaper as it will be set: a photo blurred as picked. */
internal fun WallpaperChoice.Existing.shown(): Wallpaper =
    (wallpaper as? Wallpaper.Photo)?.copy(isBlurred = isBlurred) ?: wallpaper
