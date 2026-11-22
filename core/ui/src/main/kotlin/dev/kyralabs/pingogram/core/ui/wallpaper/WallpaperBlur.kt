package dev.kyralabs.pingogram.core.ui.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.scale
import coil3.size.Size
import coil3.transform.Transformation
import dev.kyralabs.pingogram.core.ui.image.gaussianBlur
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A blurred wallpaper (`docs/TDLIB.md` "Backgrounds"): the photo downscaled to fit a 450 × 450 square, then blurred
 * with a Gaussian of [sigma], which leaves no box's steps. Coil runs it once, off the main thread, and keeps the small
 * result in its memory cache under the photo's key and [cacheKey]; the wallpaper draws it scaled up. [Photo] is a
 * blurred photo wallpaper's blur, [Bar] the stronger one behind a conversation's top bar (owner decision 2026-11-22).
 */
internal class WallpaperBlur private constructor(
    override val cacheKey: String,
    private val sigma: Float,
) : Transformation() {
    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        val scale = min(1f, BLUR_SIDE_PX.toFloat() / max(input.width, input.height))
        val width = (input.width * scale).roundToInt().coerceAtLeast(1)
        val height = (input.height * scale).roundToInt().coerceAtLeast(1)
        val small = input.scale(width, height)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== input) small.recycle()
        return Bitmap.createBitmap(
            gaussianBlur(pixels, width, height, sigma),
            width,
            height,
            Bitmap.Config.ARGB_8888,
        )
    }

    companion object {
        /** A blurred photo wallpaper: the spread of a box blur of radius 12 (25 pixels wide), √((25² − 1) / 12). */
        val Photo = WallpaperBlur("wallpaper-blur", sigma = 7.21f)

        /** Behind a top bar: the spread of a box blur of radius 32 (65 pixels wide), √((65² − 1) / 12). */
        val Bar = WallpaperBlur("wallpaper-bar-blur", sigma = 18.76f)
    }
}

/** The square a blurred wallpaper fits before its blur, in pixels; its photo is decoded about that size. */
internal const val BLUR_SIDE_PX = 450
