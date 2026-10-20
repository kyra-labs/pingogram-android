package dev.kyralabs.pingogram.core.ui.sticker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * [StickerAnimationDecoder] on Lottie (`com.airbnb.android:lottie`, docs/adr/0008-lottie-for-animated-stickers.md): a
 * TGS file is gzipped Lottie JSON, which [LottieCompositionFactory] parses into a [LottieComposition]; each renderer
 * draws it with a [LottieDrawable] of its own into a software [Canvas] over the target bitmap, on whatever thread
 * calls it. Lottie's own cache is not used (no cache key): [AnimatedStickers] keeps the compositions.
 */
class LottieStickerDecoder : StickerAnimationDecoder {
    override fun decode(file: File): StickerAnimation? {
        val composition =
            try {
                GZIPInputStream(file.inputStream().buffered()).use { stream ->
                    LottieCompositionFactory.fromJsonInputStreamSync(stream, null).value
                }
            } catch (_: IOException) {
                null
            }
        return composition?.takeIf { it.durationFrames >= 1f }?.let(::LottieStickerAnimation)
    }
}

private class LottieStickerAnimation(
    private val composition: LottieComposition,
) : StickerAnimation {
    override val frameCount: Int = composition.durationFrames.roundToInt().coerceAtLeast(1)
    override val frameRate: Float = composition.frameRate.takeIf { it > 0f } ?: DEFAULT_FRAME_RATE

    override fun newRenderer(): StickerFrameRenderer = LottieFrameRenderer(composition)
}

private class LottieFrameRenderer(
    private val composition: LottieComposition,
) : StickerFrameRenderer {
    private val drawable = LottieDrawable().also { it.setComposition(composition) }
    private val startFrame = composition.startFrame.roundToInt()
    private val canvas = Canvas()

    override fun render(
        frame: Int,
        bitmap: Bitmap,
    ) {
        bitmap.eraseColor(Color.TRANSPARENT)
        val bounds = composition.bounds
        val scale = min(bitmap.width / bounds.width().toFloat(), bitmap.height / bounds.height().toFloat())
        val width = (bounds.width() * scale).roundToInt()
        val height = (bounds.height() * scale).roundToInt()
        val left = (bitmap.width - width) / 2
        val top = (bitmap.height - height) / 2
        drawable.setBounds(left, top, left + width, top + height)
        drawable.frame = startFrame + frame
        canvas.setBitmap(bitmap)
        drawable.draw(canvas)
    }
}

/** Telegram's animated stickers run at 30 or 60 frames per second; a file without a rate gets the lower one. */
private const val DEFAULT_FRAME_RATE = 30f
