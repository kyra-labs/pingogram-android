package dev.kyralabs.pingogram.core.ui.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import kotlin.math.ceil
import kotlin.math.exp

/**
 * A photo's or video's [minithumbnail] (the 40 px JPEG TDLib sends with it), blurred, cropped to fill this box and
 * drawn scaled up with bilinear filtering; [fallback] where there is none or it cannot be decoded. What media shows
 * until its image loads, and instead of it while hidden (a spoiler, self-destructing media). [colorFilter] tints it as
 * the image over it is tinted, e.g. the dark theme's dim of photos.
 *
 * The blur is done once, on the 40 px bitmap itself ([blurredMinithumbnail], cached per minithumbnail), not with a
 * `RenderEffect` on the drawn box: that one re-rendered a blur layer the size of the box in every frame, under every
 * media bubble while a conversation scrolled and twice in every frame of the media viewer's container transform
 * (`docs/audit/performance-review-2026-11.md`, M1 and W1). Blurring before scaling up looks the same, and works before
 * Android 12 too.
 *
 * While [covered] answers true (the image over it has fully faded in), it draws nothing: read in the draw phase, so
 * nothing composes again when the image arrives, and an opaque image does not pay for what it hides.
 */
@Composable
fun BlurredMinithumbnail(
    minithumbnail: Minithumbnail?,
    fallback: Color,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    covered: () -> Boolean = NeverCovered,
) {
    val bitmap = remember(minithumbnail) { minithumbnail?.let(::blurredMinithumbnail) }
    val drawn = modifier.hiddenWhen(covered)
    if (bitmap == null) {
        Box(drawn.background(fallback))
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = drawn,
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
        )
    }
}

/** Draws nothing of this content while [hidden] answers true; read in the draw phase. */
fun Modifier.hiddenWhen(hidden: () -> Boolean): Modifier =
    if (hidden === NeverCovered) this else drawWithContent { if (!hidden()) drawContent() }

private val NeverCovered: () -> Boolean = { false }

/**
 * [minithumbnail] decoded and blurred at its own size, once: the result is kept per minithumbnail (they compare by
 * their bytes, and each is a few KB at most), so a bubble scrolling back in, or the viewer's page of a bubble just
 * shown, gets the same bitmap. Null when it cannot be decoded.
 */
fun blurredMinithumbnail(minithumbnail: Minithumbnail): ImageBitmap? {
    BlurredMinithumbnails.cache.get(minithumbnail)?.let { return it }
    val decoded = BitmapFactory.decodeByteArray(minithumbnail.jpeg, 0, minithumbnail.jpeg.size) ?: return null
    val width = decoded.width
    val height = decoded.height
    val pixels = IntArray(width * height)
    decoded.getPixels(pixels, 0, width, 0, 0, width, height)
    val blurredPixels = gaussianBlur(pixels, width, height, BLUR_SIGMA)
    val blurred = Bitmap.createBitmap(blurredPixels, width, height, Bitmap.Config.ARGB_8888)
    decoded.recycle()
    return blurred.asImageBitmap().also { BlurredMinithumbnails.cache.put(minithumbnail, it) }
}

/** The blurred minithumbnails made so far, the most recently used kept. */
private object BlurredMinithumbnails {
    val cache = LruCache<Minithumbnail, ImageBitmap>(CACHE_ENTRIES)
}

/**
 * A Gaussian blur of [sigma] pixels of the ARGB [pixels] of a [width] × [height] image, in two passes (rows, then
 * columns), its edges extended as they are (the blurred picture is cropped to its box, so nothing outside it is
 * seen). Each channel is blurred apart, alpha included.
 */
internal fun gaussianBlur(
    pixels: IntArray,
    width: Int,
    height: Int,
    sigma: Float,
): IntArray {
    if (width == 0 || height == 0) return pixels.copyOf()
    val kernel = gaussianKernel(sigma)
    val rows = IntArray(pixels.size)
    for (y in 0 until height) blurLine(pixels, rows, kernel, y * width, 1, width)
    val result = IntArray(pixels.size)
    for (x in 0 until width) blurLine(rows, result, kernel, x, width, height)
    return result
}

/** Blurs the [count] pixels of [source] from [start], [step] apart, into the same places of [target]. */
private fun blurLine(
    source: IntArray,
    target: IntArray,
    kernel: FloatArray,
    start: Int,
    step: Int,
    count: Int,
) {
    val radius = kernel.size / 2
    for (i in 0 until count) {
        var a = 0f
        var r = 0f
        var g = 0f
        var b = 0f
        for (k in kernel.indices) {
            val pixel = source[start + (i + k - radius).coerceIn(0, count - 1) * step]
            val weight = kernel[k]
            a += (pixel ushr ALPHA_SHIFT and CHANNEL) * weight
            r += (pixel ushr RED_SHIFT and CHANNEL) * weight
            g += (pixel ushr GREEN_SHIFT and CHANNEL) * weight
            b += (pixel and CHANNEL) * weight
        }
        target[start + i * step] =
            (a.channel() shl ALPHA_SHIFT) or (r.channel() shl RED_SHIFT) or (g.channel() shl GREEN_SHIFT) or b.channel()
    }
}

private fun Float.channel(): Int = (this + HALF).toInt().coerceIn(0, CHANNEL)

/** A normalised Gaussian kernel of [sigma], three sigmas each side. */
private fun gaussianKernel(sigma: Float): FloatArray {
    val radius = ceil(sigma * KERNEL_SIGMAS).toInt().coerceAtLeast(1)
    val kernel = FloatArray(radius * 2 + 1) { i -> exp(-((i - radius) * (i - radius)) / (2 * sigma * sigma)) }
    val sum = kernel.sum()
    for (i in kernel.indices) kernel[i] /= sum
    return kernel
}

/**
 * The blur the placeholders had as a `RenderEffect`: 1.5 px of the 40 px picture (enough to hide its JPEG blocks,
 * little enough to keep its shapes), which the platform's blur turns into this sigma (radius × 0.57735 + 0.5).
 */
private const val BLUR_SIGMA = 1.37f
private const val KERNEL_SIGMAS = 3f
private const val CACHE_ENTRIES = 256
private const val HALF = 0.5f
private const val CHANNEL = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
