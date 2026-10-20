package dev.kyralabs.pingogram.core.ui.sticker

import android.animation.ValueAnimator
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * TDLib file [fileId], an animated (TGS) sticker, played by [stickers] in this composable's size (`docs/PERFORMANCE.md`
 * §4): transparent until its first frame is ready, which [onFirstFrame] reports (e.g. to fade out the thumbnail
 * below), then playing in a loop while it is on screen, the window has the focus and one of the [AnimatedStickers]'
 * slots is free; still on its current frame otherwise. Its frames are drawn off the main thread and only invalidate
 * its own graphics layer: nothing recomposes or lays out again while it plays. Decorative: the component around it
 * describes the sticker.
 */
@Composable
fun AnimatedSticker(
    stickers: AnimatedStickers,
    fileId: Int,
    modifier: Modifier = Modifier,
    onFirstFrame: () -> Unit = {},
) {
    Spacer(modifier.graphicsLayer().then(AnimatedStickerElement(stickers, fileId, onFirstFrame)))
}

private data class AnimatedStickerElement(
    val stickers: AnimatedStickers,
    val fileId: Int,
    val onFirstFrame: () -> Unit,
) : ModifierNodeElement<AnimatedStickerNode>() {
    override fun create() = AnimatedStickerNode(stickers, fileId, onFirstFrame)

    override fun update(node: AnimatedStickerNode) = node.update(stickers, fileId, onFirstFrame)

    override fun InspectorInfo.inspectableProperties() {
        name = "animatedSticker"
        properties["fileId"] = fileId
    }
}

/**
 * Plays the sticker from the moment it is attached and has a size: waits for the animation and its first frame, then
 * renders frame after frame into one of two bitmaps while the other shows, holding a slot while it plays. A frame that
 * is not drawn within [OFF_SCREEN_TIMEOUT] means the sticker left the screen (a lazy list keeps items composed but
 * not placed): it gives the slot back and waits until it is drawn again. Losing the window's focus pauses it too.
 */
private class AnimatedStickerNode(
    private var stickers: AnimatedStickers,
    private var fileId: Int,
    private var onFirstFrame: () -> Unit,
) : Modifier.Node(),
    DrawModifierNode,
    LayoutAwareModifierNode,
    CompositionLocalConsumerModifierNode {
    override val shouldAutoInvalidate: Boolean get() = false

    private var size = IntSize.Zero
    private var frame: ImageBitmap? = null
    private var job: Job? = null

    /** Counts the draws, which tell the player that its last frame reached the screen. */
    private val draws = MutableStateFlow(0L)

    fun update(
        stickers: AnimatedStickers,
        fileId: Int,
        onFirstFrame: () -> Unit,
    ) {
        this.onFirstFrame = onFirstFrame
        if (stickers == this.stickers && fileId == this.fileId) return
        this.stickers = stickers
        this.fileId = fileId
        frame = null
        invalidateDraw()
        restart()
    }

    override fun onAttach() = restart()

    override fun onDetach() {
        // The node's coroutine scope is cancelled with it.
        job = null
        frame = null
    }

    override fun onRemeasured(size: IntSize) {
        if (size == this.size) return
        this.size = size
        restart()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        frame?.let(::drawImage)
        draws.value++
    }

    private fun restart() {
        job?.cancel()
        job = null
        val size = size
        if (!isAttached || size.width <= 0 || size.height <= 0) return
        job = coroutineScope.launch { play(size.width, size.height) }
    }

    private suspend fun play(
        width: Int,
        height: Int,
    ) {
        val stickers = stickers
        val animation = stickers.animation(fileId) ?: return
        show(stickers.firstFrame(fileId, animation, width, height).asImageBitmap())
        onFirstFrame()
        if (!stickers.animate || animation.frameCount < 2 || !ValueAnimator.areAnimatorsEnabled()) return
        val windowInfo = currentValueOf(LocalWindowInfo)
        val player = Player(stickers, animation, width, height)
        while (true) {
            awaitOnScreen(windowInfo)
            stickers.slots.withPermit { player.playWhile { windowInfo.isWindowFocused } }
        }
    }

    /** Returns once the window has the focus and this sticker was drawn after asking for it. */
    private suspend fun awaitOnScreen(windowInfo: WindowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        val drawn = draws.value
        invalidateDraw()
        draws.first { it > drawn }
    }

    private fun show(bitmap: ImageBitmap) {
        frame = bitmap
        invalidateDraw()
    }

    /** Renders the frames of [animation] in turn, at most [AnimatedStickers.MAX_FRAMES_PER_SECOND] per second. */
    private inner class Player(
        private val stickers: AnimatedStickers,
        private val animation: StickerAnimation,
        private val width: Int,
        private val height: Int,
    ) {
        private val renderer = animation.newRenderer()
        private val step = ceil(animation.frameRate / AnimatedStickers.MAX_FRAMES_PER_SECOND).toInt().coerceAtLeast(1)
        private val frameDuration = (step * MILLIS_PER_SECOND / animation.frameRate).toDouble().milliseconds
        private val buffers = arrayOfNulls<Bitmap>(2)
        private val images = arrayOfNulls<ImageBitmap>(2)
        private var next = 0
        private var frame = 0

        /** Plays while [condition] holds and every frame is drawn in time. */
        suspend fun playWhile(condition: () -> Boolean) {
            while (condition()) {
                val start = TimeSource.Monotonic.markNow()
                frame = (frame + step) % animation.frameCount
                val index = next
                withContext(stickers.renderDispatcher) {
                    val bitmap =
                        buffers[index] ?: createBitmap(width, height).also {
                            buffers[index] = it
                        }
                    renderer.render(frame, bitmap)
                }
                val image = images[index] ?: buffers.getValue(index).asImageBitmap().also { images[index] = it }
                val drawn = draws.value
                show(image)
                next = 1 - index
                withTimeoutOrNull(OFF_SCREEN_TIMEOUT) { draws.first { it > drawn } } ?: return
                delay(frameDuration - start.elapsedNow())
            }
        }

        private fun Array<Bitmap?>.getValue(index: Int): Bitmap = checkNotNull(get(index))
    }
}

/** Long enough for a frame to be drawn on a busy device; any longer and an off-screen sticker keeps its slot. */
private val OFF_SCREEN_TIMEOUT = 250.milliseconds

private const val MILLIS_PER_SECOND = 1000f
