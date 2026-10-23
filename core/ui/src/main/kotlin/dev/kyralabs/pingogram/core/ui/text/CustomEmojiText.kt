package dev.kyralabs.pingogram.core.ui.text

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.domain.model.CustomEmoji
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.sticker.CustomEmojiFrames
import dev.kyralabs.pingogram.core.ui.sticker.CustomEmojiRenderer
import dev.kyralabs.pingogram.core.ui.sticker.repaintFilter
import kotlin.math.roundToInt

/** The tag of the string annotations over a text's custom emoji; their values are the custom emoji ids. */
const val CUSTOM_EMOJI_TAG = "pingogram.customEmoji"

/**
 * A custom emoji's [CUSTOM_EMOJI_TAG] annotation from [start] until [end], its id as the value, for an entity of
 * [type]; nothing for other entities.
 */
internal fun AnnotatedString.Builder.addCustomEmoji(
    type: TextEntityType,
    start: Int,
    end: Int,
) {
    val emoji = type as? TextEntityType.CustomEmoji ?: return
    addStringAnnotation(CUSTOM_EMOJI_TAG, emoji.customEmojiId.toString(), start, end)
}

/** Whether this text has custom emoji for [customEmoji] to draw. */
fun AnnotatedString.hasCustomEmoji(): Boolean = hasStringAnnotations(CUSTOM_EMOJI_TAG, 0, length)

/**
 * This text without formatting, its custom emoji marked for [customEmoji]: for a text that shows nothing else, as
 * the large emoji of an emoji-only message.
 */
fun FormattedText.customEmojiText(): AnnotatedString =
    AnnotatedString.Builder(text).run {
        for (entity in entities) {
            val start = entity.offset.coerceIn(0, text.length)
            val end = (entity.offset + entity.length).coerceIn(start, text.length)
            if (end > start) addCustomEmoji(entity.type, start, end)
        }
        toAnnotatedString()
    }

/**
 * A text field's custom emoji, for [customEmoji]: the field's [entities] now (its text layout holds no annotations)
 * and how far it is scrolled ([scrollOffset], in pixels).
 */
class CustomEmojiField(
    val entities: () -> List<TextEntity>,
    val scrollOffset: () -> Int,
)

/**
 * Draws the custom emoji of a text (`docs/DESIGN_SYSTEM.md` §4, Custom emoji) over their fallback emoji, which the
 * text keeps (so copying and screen readers get the emoji): for each custom emoji annotated in the text of [layout]
 * ([CUSTOM_EMOJI_TAG], `toAnnotatedString`), or each of a text [field]'s entities, its picture from [renderer] in a
 * square as large as the fallback emoji's glyph box allows (an emoji's width, as tall as the line allows), centred on
 * it. Until the picture is there, and where it never loads, the fallback emoji shows: no blank space. Once it is, the
 * glyph's box is cut out of the text (a clip, so nothing is laid out again) and the picture drawn there, in [tint] for
 * a custom emoji that takes the text's color (`needsRepainting`). In a text field the cut leaves the cursor's width at
 * both ends, so the cursor shows beside a custom emoji. A custom emoji inside a hidden spoiler stays hidden.
 *
 * The places are computed once per layout, in the draw phase; pictures the caches hold show in the text's first frame,
 * the others load and play in the modifier's own coroutines, and a new frame redraws only this text's own graphics
 * layer: nothing composes or lays out again (`docs/PERFORMANCE.md` §4).
 * Without a [renderer] it adds nothing.
 */
fun Modifier.customEmoji(
    renderer: CustomEmojiRenderer?,
    layout: TextBlocksLayout,
    tint: ColorProducer,
    field: CustomEmojiField? = null,
): Modifier =
    if (renderer == null) {
        this
    } else {
        graphicsLayer().then(CustomEmojiTextElement(renderer, layout, tint, field))
    }

/**
 * Where a custom emoji of a laid-out text goes: [glyph], the fallback emoji's box (its line's height), and [picture],
 * the square in it the picture fills.
 */
internal data class CustomEmojiPlacement(
    val customEmojiId: Long,
    val glyph: Rect,
    val picture: Rect,
)

/**
 * The places of the custom emoji annotated in this layout's text ([CUSTOM_EMOJI_TAG]), or of [entities] when given;
 * none for those within a hidden spoiler, on a line cut short, or without a glyph to replace.
 */
internal fun TextLayoutResult.customEmojiPlacements(entities: List<TextEntity>? = null): List<CustomEmojiPlacement> {
    val text = layoutInput.text
    val ranges =
        entities
            ?.mapNotNull { entity ->
                (entity.type as? TextEntityType.CustomEmoji)?.let {
                    Triple(it.customEmojiId, entity.offset, entity.offset + entity.length)
                }
            }
            ?: text.getStringAnnotations(CUSTOM_EMOJI_TAG, 0, text.length).mapNotNull { annotation ->
                annotation.item.toLongOrNull()?.let { Triple(it, annotation.start, annotation.end) }
            }
    if (ranges.isEmpty()) return emptyList()
    val hidden =
        text.getStringAnnotations(TEXT_BLOCK_TAG, 0, text.length).filter { it.item == TextBlock.SPOILER }
    return ranges.mapNotNull { (id, start, end) ->
        val inText = start in 0..<end && end <= text.length
        val spoiled = hidden.any { it.start < end && start < it.end }
        if (inText && !spoiled) placement(id, start, end) else null
    }
}

/** The place of a custom emoji [id] over [start] until [end]; null where there is none to draw. */
private fun TextLayoutResult.placement(
    id: Long,
    start: Int,
    end: Int,
): CustomEmojiPlacement? {
    val line = getLineForOffset(start)
    val onOneLine = line < lineCount && !isLineEllipsized(line) && getLineForOffset(end - 1) == line
    // The first and the last character's boxes: their outer edges, in either direction.
    val first = getBoundingBox(start)
    val last = getBoundingBox(end - 1)
    val glyph =
        Rect(
            minOf(first.left, last.left),
            getLineTop(line),
            maxOf(first.right, last.right),
            getLineBottom(line),
        )
    val side = minOf(glyph.width, glyph.height)
    return if (onOneLine && side > 0f) CustomEmojiPlacement(id, glyph, Rect(glyph.center, side / 2)) else null
}

private data class CustomEmojiTextElement(
    val renderer: CustomEmojiRenderer,
    val layout: TextBlocksLayout,
    val tint: ColorProducer,
    val field: CustomEmojiField?,
) : ModifierNodeElement<CustomEmojiTextNode>() {
    override fun create() = CustomEmojiTextNode(renderer, layout, tint, field)

    override fun update(node: CustomEmojiTextNode) = node.update(renderer, layout, tint, field)

    override fun InspectorInfo.inspectableProperties() {
        name = "customEmoji"
    }
}

/**
 * Keeps a picture per custom emoji the text places ([CustomEmojiFrames]), brought in line with the text's layout and
 * the renderer's custom emoji whenever the text is measured and whenever either changes (read in [observeReads]),
 * before it draws, so a text that scrolls in shows the pictures the caches hold in its first frame; draws the text
 * with the glyphs of those that have one cut out, and the pictures over them.
 */
private class CustomEmojiTextNode(
    private var renderer: CustomEmojiRenderer,
    private var layout: TextBlocksLayout,
    private var tint: ColorProducer,
    private var field: CustomEmojiField?,
) : Modifier.Node(),
    DrawModifierNode,
    LayoutAwareModifierNode,
    ObserverModifierNode,
    CompositionLocalConsumerModifierNode {
    override val shouldAutoInvalidate: Boolean get() = false

    private val frames = CustomEmojiFrames(::invalidateDraw)

    private var placedLayout: TextLayoutResult? = null
    private var placedEntities: List<TextEntity>? = null
    private var placements: List<CustomEmojiPlacement> = emptyList()

    /** What [frames] were last brought in line with. */
    private var syncedPlacements: List<CustomEmojiPlacement>? = null
    private var syncedEmoji: Map<Long, CustomEmoji>? = null

    private val cut = Path()
    private var repaintColor = Color.Unspecified
    private var repaint: ColorFilter? = null

    fun update(
        renderer: CustomEmojiRenderer,
        layout: TextBlocksLayout,
        tint: ColorProducer,
        field: CustomEmojiField?,
    ) {
        this.tint = tint
        if (renderer != this.renderer || layout != this.layout || field != this.field) {
            this.renderer = renderer
            this.layout = layout
            this.field = field
            reset()
        }
        invalidateDraw()
    }

    override fun onDetach() = reset()

    // Not onPlaced, which comes in every frame a list scrolls: a new layout comes with a measure, or through the reads.
    override fun onRemeasured(size: IntSize) = follow()

    override fun onObservedReadsChanged() = follow()

    /** Syncs now, and again when the layout, a field's entities or the renderer's custom emoji change. */
    private fun follow() {
        if (!isAttached) return
        var changed = false
        observeReads { changed = sync(placementsOf(layout.result)) }
        if (changed) invalidateDraw()
    }

    /** Stops and forgets every picture; the next draw starts them again. The node's jobs end with its scope too. */
    private fun reset() {
        frames.clear()
        placedLayout = null
        placedEntities = null
        placements = emptyList()
        syncedPlacements = null
        syncedEmoji = null
    }

    /** The places of [result]'s custom emoji, computed again only for a new layout (or a field's new entities). */
    private fun placementsOf(result: TextLayoutResult?): List<CustomEmojiPlacement> {
        val entities = field?.entities?.invoke()
        if (result === placedLayout && entities == placedEntities) return placements
        placedLayout = result
        placedEntities = entities
        placements = result?.customEmojiPlacements(entities).orEmpty()
        return placements
    }

    /**
     * Brings [frames] in line with [placements] and the renderer's custom emoji, if either changed; whether a picture
     * changed.
     */
    private fun sync(placements: List<CustomEmojiPlacement>): Boolean {
        val emoji = renderer.emoji
        if (placements === syncedPlacements && emoji === syncedEmoji) return false
        syncedPlacements = placements
        syncedEmoji = emoji
        val sizes = HashMap<Long, Int>()
        for (placement in placements) {
            val side = placement.picture.width.roundToInt()
            sizes[placement.customEmojiId] = maxOf(side, sizes[placement.customEmojiId] ?: 0)
        }
        return frames.sync(renderer, sizes, emoji, coroutineScope, currentValueOf(LocalWindowInfo))
    }

    override fun ContentDrawScope.draw() {
        val placements = placementsOf(layout.result)
        sync(placements)
        if (placements.isEmpty() || !frames.hasFrames) {
            drawContent()
        } else {
            val offset = Offset(0f, -(field?.scrollOffset?.invoke() ?: 0).toFloat())
            val inset = if (field != null) CURSOR_ROOM.toPx() else 0f
            cut.rewind()
            for (placement in placements) {
                if (frames.frame(placement.customEmojiId) == null) continue
                val glyph = placement.glyph.translate(offset)
                cut.addRect(Rect(glyph.left + inset, glyph.top, glyph.right - inset, glyph.bottom))
            }
            clipPath(cut, ClipOp.Difference) { this@draw.drawContent() }
            clipRect { drawPictures(placements, offset) }
        }
        frames.drawn()
    }

    private fun DrawScope.drawPictures(
        placements: List<CustomEmojiPlacement>,
        offset: Offset,
    ) {
        for (placement in placements) {
            val frame = frames.frame(placement.customEmojiId) ?: continue
            val picture = placement.picture.translate(offset)
            val side = picture.width.roundToInt()
            // The filter of the tint's color, made again only when the color changes.
            val repainted = frames.isRepainted(placement.customEmojiId)
            if (repainted && (tint() != repaintColor || repaint == null)) {
                repaintColor = tint()
                repaint = repaintFilter(repaintColor)
            }
            drawImage(
                image = frame,
                dstOffset = IntOffset(picture.left.roundToInt(), picture.top.roundToInt()),
                dstSize = IntSize(side, side),
                colorFilter = if (repainted) repaint else null,
            )
        }
    }
}

/** Half a text field's cursor, kept clear at each end of a custom emoji in a field. */
private val CURSOR_ROOM = 1.dp
