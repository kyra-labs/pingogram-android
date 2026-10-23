package dev.kyralabs.pingogram.feature.chat.ui

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import dev.kyralabs.pingogram.core.domain.model.CustomEmoji
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.sticker.CustomEmojiRenderer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

/**
 * Custom emoji for tests and screenshots: still (WebP) ones whose pictures come from [testCustomEmojiRenderer] at once,
 * drawn on the test's canvas, with no TDLib file behind them.
 */
internal object CustomEmojiFakes {
    /** A star, pink and violet, Pingogram's own test artwork. */
    const val STAR = 1L

    /** A green leaf. */
    const val LEAF = 2L

    /** A single-color heart that takes the text's color (`needsRepainting`). */
    const val HEART = 3L

    val all: Map<Long, CustomEmoji> =
        listOf(STAR to "⭐", LEAF to "🍀", HEART to "❤️").associate { (id, emoji) ->
            id to
                CustomEmoji(
                    id = id,
                    sticker =
                        Sticker(
                            setId = 1,
                            width = 100,
                            height = 100,
                            emoji = emoji,
                            format = StickerFormat.Webp,
                            thumbnail = null,
                            file = FileRef(id.toInt(), size = 1_000),
                        ),
                    needsRepainting = id == HEART,
                )
        }

    /** [text] with each of [emoji] (its fallback emoji, in order) as custom emoji of that id. */
    fun text(
        text: String,
        vararg emoji: Pair<String, Long>,
    ): FormattedText {
        var from = 0
        val entities =
            emoji.map { (fallback, id) ->
                val offset = text.indexOf(fallback, from)
                check(offset >= 0) { "No $fallback after $from" }
                from = offset + fallback.length
                TextEntity(offset, fallback.length, TextEntityType.CustomEmoji(id))
            }
        return FormattedText(text, entities.toImmutableList())
    }

    /** The picture of TDLib file [fileId] (a custom emoji's id here) at [size] pixels. */
    fun picture(
        fileId: Int,
        size: Int,
    ): ImageBitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val half = size / 2f
        when (fileId.toLong()) {
            STAR -> {
                paint.color = PINK
                canvas.drawCircle(half, half, half, paint)
                paint.color = VIOLET
                canvas.drawCircle(half, half, half / 2, paint)
            }

            LEAF -> {
                paint.color = GREEN
                canvas.drawOval(size * 0.1f, size * 0.25f, size * 0.9f, size * 0.75f, paint)
            }

            else -> {
                // Black: the text's color replaces it.
                paint.color = BLACK
                canvas.drawCircle(half * 0.6f, half * 0.7f, half * 0.45f, paint)
                canvas.drawCircle(half * 1.4f, half * 0.7f, half * 0.45f, paint)
                canvas.drawRect(half * 0.4f, half * 0.8f, half * 1.6f, half * 1.1f, paint)
                canvas.drawCircle(half, half * 1.5f, half * 0.35f, paint)
            }
        }
        return bitmap.asImageBitmap()
    }

    // Test artwork, not theme colors.
    private const val PINK = 0xFFE91E63.toInt()
    private const val VIOLET = 0xFF7C4DFF.toInt()
    private const val GREEN = 0xFF43A047.toInt()
    private const val BLACK = 0xFF000000.toInt()
}

/** A renderer of [CustomEmojiFakes]' pictures, with no animated stickers, knowing [known] at first. */
internal fun testCustomEmojiRenderer(known: Map<Long, CustomEmoji> = CustomEmojiFakes.all): CustomEmojiRenderer =
    CustomEmojiRenderer(stickers = null, stills = { fileId, size -> CustomEmojiFakes.picture(fileId, size) }).apply {
        emoji = known.toImmutableMap()
    }
