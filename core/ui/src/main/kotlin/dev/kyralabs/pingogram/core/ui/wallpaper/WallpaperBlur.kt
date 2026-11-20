package dev.kyralabs.pingogram.core.ui.wallpaper

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import dev.kyralabs.pingogram.core.ui.image.gaussianBlur
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The blur of a blurred photo wallpaper (`docs/TDLIB.md` "Backgrounds"): the photo downscaled to fit a 450 × 450
 * square, then blurred with the spread of a box of radius 12, as a Gaussian, which leaves no box's steps. Coil runs it
 * once, off the main thread, and keeps the small result in its memory cache under the photo's key and this one; the
 * wallpaper draws it scaled up.
 */
internal object WallpaperBlur : Transformation() {
    override val cacheKey: String = "wallpaper-blur"

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        val scale = min(1f, BLUR_SIDE / max(input.width, input.height))
        val width = (input.width * scale).roundToInt().coerceAtLeast(1)
        val height = (input.height * scale).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(input, width, height, true)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== input) small.recycle()
        return Bitmap.createBitmap(
            gaussianBlur(pixels, width, height, BLUR_SIGMA),
            width,
            height,
            Bitmap.Config.ARGB_8888,
        )
    }
}

/** The square a blurred wallpaper fits before its blur, in pixels. */
private const val BLUR_SIDE = 450f

/** The spread of a box blur of radius 12 (25 pixels wide) as a Gaussian's sigma: √((25² − 1) / 12). */
private const val BLUR_SIGMA = 7.21f
