package dev.kyralabs.pingogram.core.ui.sticker

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.kyralabs.pingogram.core.domain.model.CustomEmoji

/**
 * Custom emoji [customEmojiId] on its own (a reaction's), drawn by [renderer] as texts draw theirs (`customEmoji`):
 * its picture in the largest square this composable's size holds, centred, in [tint] where it takes the text's color;
 * [placeholder] until it is there, and where it never loads. A newly loaded emoji or a new frame redraws only its own
 * graphics layer. Decorative: the component around it describes it.
 */
@Composable
fun CustomEmojiImage(
    renderer: CustomEmojiRenderer,
    customEmojiId: Long,
    tint: ColorProducer,
    modifier: Modifier = Modifier,
    placeholder: DrawScope.() -> Unit = {},
) {
    Spacer(modifier.graphicsLayer().then(CustomEmojiImageElement(renderer, customEmojiId, tint, placeholder)))
}

private data class CustomEmojiImageElement(
    val renderer: CustomEmojiRenderer,
    val customEmojiId: Long,
    val tint: ColorProducer,
    val placeholder: DrawScope.() -> Unit,
) : ModifierNodeElement<CustomEmojiImageNode>() {
    override fun create() = CustomEmojiImageNode(renderer, customEmojiId, tint, placeholder)

    override fun update(node: CustomEmojiImageNode) = node.update(renderer, customEmojiId, tint, placeholder)

    override fun InspectorInfo.inspectableProperties() {
        name = "customEmojiImage"
        properties["customEmojiId"] = customEmojiId
    }
}

/**
 * Keeps the emoji's picture in [CustomEmojiFrames], brought in line with its size and the renderer's custom emoji
 * whenever it is measured and whenever the custom emoji change (read in [observeReads]), before it draws, so a cached
 * picture shows in its first frame.
 */
private class CustomEmojiImageNode(
    private var renderer: CustomEmojiRenderer,
    private var customEmojiId: Long,
    private var tint: ColorProducer,
    private var placeholder: DrawScope.() -> Unit,
) : Modifier.Node(),
    DrawModifierNode,
    LayoutAwareModifierNode,
    ObserverModifierNode,
    CompositionLocalConsumerModifierNode {
    override val shouldAutoInvalidate: Boolean get() = false

    private val frames = CustomEmojiFrames(::invalidateDraw)
    private var side = 0
    private var syncedSide = 0
    private var syncedEmoji: CustomEmoji? = null

    fun update(
        renderer: CustomEmojiRenderer,
        customEmojiId: Long,
        tint: ColorProducer,
        placeholder: DrawScope.() -> Unit,
    ) {
        this.tint = tint
        this.placeholder = placeholder
        if (renderer != this.renderer || customEmojiId != this.customEmojiId) {
            this.renderer = renderer
            this.customEmojiId = customEmojiId
            reset()
        }
        invalidateDraw()
    }

    override fun onDetach() = reset()

    override fun onRemeasured(size: IntSize) {
        side = minOf(size.width, size.height)
        follow()
    }

    override fun onObservedReadsChanged() = follow()

    /** Syncs now, and again when the renderer's custom emoji change. */
    private fun follow() {
        if (!isAttached) return
        var changed = false
        observeReads { changed = sync(side) }
        if (changed) invalidateDraw()
    }

    private fun reset() {
        frames.clear()
        syncedSide = 0
        syncedEmoji = null
    }

    /** Brings [frames] in line with the emoji at [side] pixels, if either changed; whether its picture changed. */
    private fun sync(side: Int): Boolean {
        val emoji = renderer.emoji[customEmojiId]
        if (side == syncedSide && emoji === syncedEmoji) return false
        syncedSide = side
        syncedEmoji = emoji
        val known = if (emoji == null) emptyMap() else mapOf(customEmojiId to emoji)
        val windowInfo = currentValueOf(LocalWindowInfo)
        return frames.sync(renderer, mapOf(customEmojiId to side), known, coroutineScope, windowInfo)
    }

    override fun ContentDrawScope.draw() {
        sync(side)
        val frame = frames.frame(customEmojiId)
        if (frame == null) {
            placeholder()
        } else {
            val side = minOf(size.width, size.height).toInt()
            drawImage(
                image = frame,
                dstOffset = IntOffset(((size.width - side) / 2).toInt(), ((size.height - side) / 2).toInt()),
                dstSize = IntSize(side, side),
                colorFilter = if (frames.isRepainted(customEmojiId)) repaintFilter(tint()) else null,
            )
        }
        frames.drawn()
    }
}
