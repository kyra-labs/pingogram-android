package dev.kyralabs.pingogram.core.ui.sticker

import android.animation.ValueAnimator
import android.graphics.Bitmap
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.WindowInfo
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Renders the frames of [animation] in turn at [width] × [height] pixels, at most
 * [AnimatedStickers.MAX_FRAMES_PER_SECOND] per second, into one of two bitmaps while the other shows, and hands each to
 * [show]: what an animated sticker ([AnimatedSticker]) and the custom emoji of a text (`customEmoji`) play with.
 * [draws] counts the draws of what shows the frames: a frame that is not drawn within [OFF_SCREEN_TIMEOUT] means it
 * left the screen (a lazy list keeps items composed but not placed).
 */
internal class StickerPlayer(
    private val stickers: AnimatedStickers,
    private val animation: StickerAnimation,
    private val width: Int,
    private val height: Int,
    private val draws: StateFlow<Long>,
    private val show: (ImageBitmap) -> Unit,
) {
    private val renderer = animation.newRenderer()
    private val step = ceil(animation.frameRate / AnimatedStickers.MAX_FRAMES_PER_SECOND).toInt().coerceAtLeast(1)
    private val frameDuration = (step * MILLIS_PER_SECOND / animation.frameRate).toDouble().milliseconds
    private val buffers = arrayOfNulls<Bitmap>(2)
    private val images = arrayOfNulls<ImageBitmap>(2)
    private var next = 0
    private var frame = 0

    /**
     * Plays for as long as the caller runs: whenever the window has the focus and what shows the frames is on screen
     * (drawn after [requestDraw]), holding one of the [AnimatedStickers.slots] while it plays.
     */
    suspend fun playOnScreen(
        windowInfo: WindowInfo,
        requestDraw: () -> Unit,
    ) {
        while (true) {
            awaitOnScreen(windowInfo, requestDraw)
            stickers.slots.withPermit { playWhile { windowInfo.isWindowFocused && !stickers.still.value } }
            if (stickers.still.value) showFirstFrame()
        }
    }

    /** Power saving turned on: back to the first frame, which stays until it is off again. */
    private suspend fun showFirstFrame() {
        frame = 0
        val index = next
        withContext(stickers.renderDispatcher) {
            val bitmap = buffers[index] ?: createBitmap(width, height).also { buffers[index] = it }
            renderer.render(frame, bitmap)
        }
        show(images[index] ?: checkNotNull(buffers[index]).asImageBitmap().also { images[index] = it })
        next = 1 - index
    }

    /** Returns once power saving lets it play, the window has the focus and the frames were drawn after asking. */
    private suspend fun awaitOnScreen(
        windowInfo: WindowInfo,
        requestDraw: () -> Unit,
    ) {
        stickers.still.first { !it }
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        val drawn = draws.value
        requestDraw()
        draws.first { it > drawn }
    }

    /** Plays while [condition] holds and every frame is drawn in time. */
    private suspend fun playWhile(condition: () -> Boolean) {
        while (condition()) {
            val start = TimeSource.Monotonic.markNow()
            frame = (frame + step) % animation.frameCount
            val index = next
            withContext(stickers.renderDispatcher) {
                val bitmap = buffers[index] ?: createBitmap(width, height).also { buffers[index] = it }
                renderer.render(frame, bitmap)
            }
            val image = images[index] ?: checkNotNull(buffers[index]).asImageBitmap().also { images[index] = it }
            val drawn = draws.value
            show(image)
            next = 1 - index
            withTimeoutOrNull(OFF_SCREEN_TIMEOUT) { draws.first { it > drawn } } ?: return
            delay(frameDuration - start.elapsedNow())
        }
    }
}

/** Whether [animation] plays at all: more than one frame, animations on, and not in previews or screenshots. */
internal fun AnimatedStickers.plays(animation: StickerAnimation): Boolean =
    animate && animation.frameCount >= 2 && ValueAnimator.areAnimatorsEnabled()

/** Long enough for a frame to be drawn on a busy device; any longer and an off-screen sticker keeps its slot. */
private val OFF_SCREEN_TIMEOUT = 250.milliseconds

private const val MILLIS_PER_SECOND = 1000f
