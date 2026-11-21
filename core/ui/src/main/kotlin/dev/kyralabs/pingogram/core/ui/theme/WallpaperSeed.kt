package dev.kyralabs.pingogram.core.ui.theme

import dev.kyralabs.pingogram.core.ui.theme.mcu.QuantizerCelebi
import dev.kyralabs.pingogram.core.ui.theme.mcu.Score

// The seed color a chat's colors come from (docs/adr/0041-chat-colors-from-the-wallpaper.md): computed once per
// wallpaper and kept (WallpaperSeedRepository), so opening a chat never analyses an image.

/** How many colors Celebi reduces a photo to before Score picks one, as Android does for the system wallpaper. */
private const val MAX_COLORS = 128

/**
 * The seed of a photo wallpaper from its [pixels] (ARGB, a downscaled copy, about 112 × 112 like Android's): Material's
 * pick (Celebi, then Score). A photo without a colorful color (black and white, say) gives its most common grey
 * instead of Score's Google Blue, so its chat's colors stay neutral, as the photo is. Takes tens of milliseconds:
 * call it off the main thread.
 */
fun wallpaperSeed(pixels: IntArray): Int {
    val colors = QuantizerCelebi.quantize(pixels, MAX_COLORS)
    val colorful = Score.rank(colors, desired = 1, fallbackColorArgb = NO_SEED).first()
    return when {
        colorful != NO_SEED -> colorful
        colors.isEmpty() -> DEFAULT_SEED
        else -> Score.rank(colors, desired = 1, filter = false).first()
    }
}

/**
 * The seed of a fill's [colors] (ARGB, the colors it is drawn in): the one Score prefers, greys included, since a fill
 * has no image to analyse. A pattern has none: it is drawn in the theme's own colors, so its chat keeps them.
 */
fun fillSeed(colors: List<Int>): Int =
    if (colors.isEmpty()) DEFAULT_SEED else Score.rank(colors.associateWith { 1 }, desired = 1, filter = false).first()

/** Score's own fallback, Google Blue, for a wallpaper without a pixel. */
private const val DEFAULT_SEED = 0xFF4285F4.toInt()

/** A color Celebi never gives (it answers opaque colors): what Score answers here when nothing is colorful enough. */
private const val NO_SEED = 0
