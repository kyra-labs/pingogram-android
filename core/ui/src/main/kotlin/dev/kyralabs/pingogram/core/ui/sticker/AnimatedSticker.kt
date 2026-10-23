package dev.kyralabs.pingogram.core.ui.sticker

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
 * plays it with a [StickerPlayer], which renders frame after frame into one of two bitmaps while the other shows,
 * holding a slot while it plays. A frame that is not drawn in time means the sticker left the screen (a lazy list keeps
 * items composed but not placed): it gives the slot back and waits until it is drawn again. Losing the window's focus
 * pauses it too.
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
        if (!stickers.plays(animation)) return
        StickerPlayer(stickers, animation, width, height, draws, ::show)
            .playOnScreen(currentValueOf(LocalWindowInfo), ::invalidateDraw)
    }

    private fun show(bitmap: ImageBitmap) {
        frame = bitmap
        invalidateDraw()
    }
}
