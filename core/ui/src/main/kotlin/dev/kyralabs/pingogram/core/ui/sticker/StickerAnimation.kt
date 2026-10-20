package dev.kyralabs.pingogram.core.ui.sticker

import android.graphics.Bitmap
import java.io.File

// The seam between Pingogram and the library that draws animated stickers (docs/ARCHITECTURE.md, Files and media;
// docs/adr/0008-lottie-for-animated-stickers.md): AnimatedStickers and the composables know only these types, so the
// renderer can change (rlottie, a newer Lottie) without touching them.

/** Decodes an animated sticker file (TGS: gzipped Lottie JSON). */
fun interface StickerAnimationDecoder {
    /**
     * The animation in [file], or null when it holds none this decoder can draw (a damaged file, another format).
     * Blocking: called off the main thread.
     */
    fun decode(file: File): StickerAnimation?
}

/**
 * A decoded animation: [frameCount] frames at [frameRate] frames per second. Immutable and shared by everything that
 * shows the sticker; each of them draws through a renderer of its own ([newRenderer]).
 */
interface StickerAnimation {
    val frameCount: Int
    val frameRate: Float

    /** A renderer for one player: renderers are not thread-safe, the animation is. */
    fun newRenderer(): StickerFrameRenderer
}

/** Draws the frames of a [StickerAnimation]. Used by one thread at a time. */
fun interface StickerFrameRenderer {
    /** Erases [bitmap] and draws frame [frame] (0 until `frameCount`) into it, scaled to fit and centred. */
    fun render(
        frame: Int,
        bitmap: Bitmap,
    )
}
