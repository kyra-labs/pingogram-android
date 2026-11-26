package dev.kyralabs.pingogram.core.ui.sticker

import android.animation.ValueAnimator
import android.graphics.Bitmap
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.WindowInfo
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ceil
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

/**
 * Renders the frames of [animation] in turn at [width] × [height] pixels, at most
 * [AnimatedStickers.MAX_FRAMES_PER_SECOND] per second, into one of two bitmaps while the other shows, and hands each to
 * [show]: what an animated sticker ([AnimatedSticker]) and the custom emoji of a text (`customEmoji`) play with.
 * [draws] counts the draws of what shows the frames: a frame that is not drawn within [OFF_SCREEN_TIMEOUT] means it
 * left the screen (a lazy list keeps items composed but not placed).
 *
 * The frames follow the clock ([AnimatedStickers.timeSource]): one that renders late is skipped rather than shown
 * late, so a sticker keeps its pace on a busy device. Its renderer is built once it first plays, not for a sticker
 * that only ever shows its first frame (the frame cache's, [AnimatedStickers.firstFrame]).
 */
internal class StickerPlayer(
    private val stickers: AnimatedStickers,
    private val animation: StickerAnimation,
    private val width: Int,
    private val height: Int,
    private val draws: StateFlow<Long>,
    private val show: (ImageBitmap) -> Unit,
) {
    private val renderer by lazy { animation.newRenderer() }
    private val step = ceil(animation.frameRate / AnimatedStickers.MAX_FRAMES_PER_SECOND).toInt().coerceAtLeast(1)
    private val buffers = arrayOfNulls<Bitmap>(2)
    private val images = arrayOfNulls<ImageBitmap>(2)
    private var next = 0

    /** The frame showing, of the animation's [StickerAnimation.frameCount]. */
    internal var frame = 0
        private set

    /** It went through its last frame since it started showing: with looping off, it stops there. */
    private var played = false

    /** Its renderer could not draw a frame: it never plays again, nor holds a slot. */
    private var failed = false

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
            if (!acquireSlotOnScreen(requestDraw)) continue
            try {
                playWhile { windowInfo.isWindowFocused && !stickers.still.value && (stickers.loop.value || !played) }
            } finally {
                stickers.slots.release()
            }
            // Trying again would fail again, every frame.
            if (failed) awaitCancellation()
            if (stickers.still.value || (played && !stickers.loop.value)) {
                showFirstFrame()
                // Played once with looping off: it rests until looping is turned on again.
                if (!stickers.still.value) stickers.loop.first { it }
                played = false
            }
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

    /**
     * Takes one of the [AnimatedStickers.slots], waiting in line only while on screen: every [OFF_SCREEN_TIMEOUT] of
     * waiting it asks for a draw, and leaves the line, false, if none comes. A sticker that scrolled away no longer
     * holds a place, to take a slot only to find it is gone.
     */
    private suspend fun acquireSlotOnScreen(requestDraw: () -> Unit): Boolean {
        while (true) {
            if (withTimeoutOrNull(OFF_SCREEN_TIMEOUT) { stickers.slots.acquire() } != null) return true
            val drawn = draws.value
            requestDraw()
            if (withTimeoutOrNull(OFF_SCREEN_TIMEOUT) { draws.first { it > drawn } } == null) return false
        }
    }

    /** Plays while [condition] holds and every frame is drawn in time, the frames following the clock. */
    private suspend fun playWhile(condition: () -> Boolean) {
        // From the frame showing; frames counted on, past the last one into the next turns.
        val origin = stickers.timeSource.markNow() - timeOf(frame.toLong())
        var position = frame.toLong()
        while (condition()) {
            val due = origin.elapsedNow().inWholeMicroseconds * animation.frameRate / MICROS_PER_SECOND
            val upcoming = maxOf(position + step, due.toLong() / step * step)
            if (upcoming / animation.frameCount > position / animation.frameCount) played = true
            position = upcoming
            frame = (position % animation.frameCount).toInt()
            val index = next
            val rendered =
                withContext(stickers.renderDispatcher) {
                    val bitmap = buffers[index] ?: createBitmap(width, height).also { buffers[index] = it }
                    renderer.render(frame, bitmap)
                }
            if (!rendered) {
                failed = true
                return
            }
            val image = images[index] ?: checkNotNull(buffers[index]).asImageBitmap().also { images[index] = it }
            val drawn = draws.value
            show(image)
            next = 1 - index
            withTimeoutOrNull(OFF_SCREEN_TIMEOUT) { draws.first { it > drawn } } ?: return
            delay(timeOf(position + step) - origin.elapsedNow())
        }
    }

    /** When frame [position] (counted on past the last frame) is due, from the start of the animation. */
    private fun timeOf(position: Long) = (position * MICROS_PER_SECOND / animation.frameRate).toDouble().microseconds
}

/** Whether [animation] plays at all: more than one frame, animations on, and not in previews or screenshots. */
internal fun AnimatedStickers.plays(animation: StickerAnimation): Boolean =
    animate && animation.frameCount >= 2 && ValueAnimator.areAnimatorsEnabled()

/** Long enough for a frame to be drawn on a busy device; any longer and an off-screen sticker keeps its slot. */
private val OFF_SCREEN_TIMEOUT = 250.milliseconds

private const val MICROS_PER_SECOND = 1_000_000f
