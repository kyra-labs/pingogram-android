package dev.kyralabs.pingogram.core.ui.sticker

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.WindowInfo
import dev.kyralabs.pingogram.core.domain.model.CustomEmoji
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The pictures of the custom emoji one node draws (a text's, a reaction's), by id: what the caches hold at once, else
 * loaded, and played, by a job of its own in the node's scope ([CustomEmojiRenderer.play]); a new frame asks the node
 * to draw again ([requestDraw]), nothing else. Main thread only, as the node is.
 */
internal class CustomEmojiFrames(
    val requestDraw: () -> Unit,
) {
    private val pictures = HashMap<Long, Picture>()
    private val mutableDraws = MutableStateFlow(0L)

    /** Counts the node's draws, which tell the players their frames reached the screen; see [drawn]. */
    val draws: StateFlow<Long> = mutableDraws

    /** Whether any picture is ready to draw. */
    val hasFrames: Boolean get() = pictures.values.any { it.frame != null }

    /** The picture of custom emoji [id] ready to draw; null while there is none. */
    fun frame(id: Long): ImageBitmap? = pictures[id]?.frame

    /** Whether custom emoji [id] is drawn in the text's color (`needsRepainting`). */
    fun isRepainted(id: Long): Boolean = pictures[id]?.emoji?.needsRepainting == true

    /**
     * Shows the custom emoji of [sizes] (their size in pixels, by id) that [emoji] knows: those not shown yet start
     * with what the caches hold at once ([CustomEmojiRenderer.cachedFrame]), and a job in [scope] loads the rest or
     * plays them; those no longer asked for are dropped. A picture whose emoji or size changed starts again, showing
     * its last frame meanwhile. It asks for no draw itself (the node may be drawing): it says whether a picture
     * changed.
     */
    fun sync(
        renderer: CustomEmojiRenderer,
        sizes: Map<Long, Int>,
        emoji: Map<Long, CustomEmoji>,
        scope: CoroutineScope,
        windowInfo: WindowInfo,
    ): Boolean {
        val dropped = dropStale(sizes, emoji)
        var changed = dropped.isNotEmpty()
        for ((id, size) in sizes) {
            val custom = emoji[id]
            if (custom != null && id !in pictures && size > 0) {
                changed = start(renderer, id, custom, size, dropped[id], scope, windowInfo) || changed
            }
        }
        return changed
    }

    /** Stops and drops the pictures [sizes] and [emoji] no longer ask for as they are; their last frames, by id. */
    private fun dropStale(
        sizes: Map<Long, Int>,
        emoji: Map<Long, CustomEmoji>,
    ): Map<Long, ImageBitmap?> {
        val dropped = HashMap<Long, ImageBitmap?>()
        val iterator = pictures.iterator()
        while (iterator.hasNext()) {
            val (id, picture) = iterator.next()
            if (sizes[id] != picture.sizePx || emoji[id] != picture.emoji) {
                picture.job?.cancel()
                iterator.remove()
                dropped[id] = picture.frame
            }
        }
        return dropped
    }

    /**
     * Starts the picture of [custom] ([id]) at [size]: what the caches hold, else [lastFrame] meanwhile, and a job
     * that loads or plays it where needed; whether it shows a new frame at once.
     */
    @Suppress("LongParameterList") // What sync() has for each picture, passed on to the job.
    private fun start(
        renderer: CustomEmojiRenderer,
        id: Long,
        custom: CustomEmoji,
        size: Int,
        lastFrame: ImageBitmap?,
        scope: CoroutineScope,
        windowInfo: WindowInfo,
    ): Boolean {
        val cached = renderer.cachedFrame(custom, size)
        val picture = Picture(custom, size, frame = cached ?: lastFrame)
        pictures[id] = picture
        if (cached == null || renderer.mayPlay(custom)) {
            picture.job =
                scope.launch {
                    renderer.play(custom, size, this@CustomEmojiFrames, windowInfo) { frame ->
                        if (frame !== picture.frame) {
                            picture.frame = frame
                            requestDraw()
                        }
                    }
                }
        }
        return cached != null
    }

    /** The node drew: the players' frames reached the screen. */
    fun drawn() {
        mutableDraws.value++
    }

    /** Stops every picture and forgets it: the node left. */
    fun clear() {
        pictures.values.forEach { it.job?.cancel() }
        pictures.clear()
    }

    private class Picture(
        val emoji: CustomEmoji,
        val sizePx: Int,
        var frame: ImageBitmap?,
    ) {
        var job: Job? = null
    }
}

/** Draws a picture in [color]'s single color, as Telegram repaints such custom emoji in the text's color. */
internal fun repaintFilter(color: Color): ColorFilter = ColorFilter.tint(color, BlendMode.SrcIn)
