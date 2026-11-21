package dev.kyralabs.pingogram.core.ui.wallpaper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced

/**
 * The box a wallpaper covers, which layers inside it can share (`docs/DESIGN_SYSTEM.md` §13.5): mark the box with
 * [wallpaperFrame] and give the frame to each [ChatWallpaperLayer] or [PhotoPreviewWallpaperLayer] in it, and each
 * draws the part of the same wallpaper at its own place. A conversation's frame is its window: the blurred layer behind
 * the top bar and the sharp one in the message sheet line up; a preview's frame is the preview.
 */
@Stable
class WallpaperFrame internal constructor() {
    /** Where the frame's box is, set as it is placed; read once every layer is placed. */
    internal var coordinates: LayoutCoordinates? = null
}

/** A [WallpaperFrame] for this composition. */
@Composable
fun rememberWallpaperFrame(): WallpaperFrame = remember { WallpaperFrame() }

/** Marks this box as the one [frame]'s wallpaper covers. */
fun Modifier.wallpaperFrame(frame: WallpaperFrame): Modifier = onPlaced { frame.coordinates = it }
