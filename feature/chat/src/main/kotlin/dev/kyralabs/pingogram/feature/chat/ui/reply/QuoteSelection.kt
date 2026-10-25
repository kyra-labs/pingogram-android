package dev.kyralabs.pingogram.feature.chat.ui.reply

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.model.TextQuote
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The quote of [text], a message's text or caption, that the user selected in its lifted bubble (td_api.tl
 * `inputTextQuote`). A selection may run over the bubble's other texts too (its sender, its reply quote, its time), so
 * [selected] holds the selected part of each text in their order; the quote is the longest part found in [text]: at
 * its start when the selection began in a text before it, at its end when it goes on into a text after it, else where
 * it first occurs, white space around it left out; so is what lies beyond [MAX_QUOTE_LENGTH]. It keeps the
 * formatting Telegram keeps in quotes ([QUOTE_ENTITY_TYPES]), cut to its range; a custom emoji only whole. Null when no
 * part is in [text], or only white space is.
 */
internal fun quoteOf(
    text: FormattedText,
    selected: List<String>,
): TextQuote? {
    val found =
        selected
            .withIndex()
            .map { (index, part) -> IndexedValue(index, part.trim()) }
            .filter { (_, part) -> part.isNotEmpty() }
            .sortedByDescending { (_, part) -> part.length }
            .firstNotNullOfOrNull { (index, part) ->
                text.text.positionOf(part, after = index > 0, before = index < selected.lastIndex)?.let { it to part }
            } ?: return null
    val (start, part) = found
    var end = start + part.length
    if (end - start > MAX_QUOTE_LENGTH) end = start + MAX_QUOTE_LENGTH
    // Never half of a surrogate pair at the end.
    if (end < text.text.length && end > start && Character.isHighSurrogate(text.text[end - 1])) end--
    return if (end > start) {
        TextQuote(FormattedText(text.text.substring(start, end), text.quoteEntities(start, end)), start)
    } else {
        null
    }
}

/**
 * Where [part] lies in this text: at its start if the selection began before it ([after] another part), at its end if
 * it goes on [before] another part, else where it first occurs; null if it is not there.
 */
private fun String.positionOf(
    part: String,
    after: Boolean,
    before: Boolean,
): Int? =
    when {
        after && startsWith(part) -> 0
        before && endsWith(part) -> length - part.length
        else -> indexOf(part).takeIf { it >= 0 }
    }

/** The entities of the kinds a quote keeps, within [start] to [end], moved to the quote's own offsets. */
private fun FormattedText.quoteEntities(
    start: Int,
    end: Int,
): ImmutableList<TextEntity> =
    entities
        .filter { it.type in QUOTE_ENTITY_TYPES || it.type is TextEntityType.CustomEmoji }
        .mapNotNull { entity ->
            val from = maxOf(entity.offset, start)
            val to = minOf(entity.offset + entity.length, end)
            val whole = from == entity.offset && to == entity.offset + entity.length
            when {
                to <= from -> null
                entity.type is TextEntityType.CustomEmoji && !whole -> null
                else -> entity.copy(offset = from - start, length = to - from)
            }
        }.toImmutableList()

/**
 * The formatting Telegram keeps in a quote (td_api.tl `inputTextQuote.text`: bold, italic, underline, strikethrough,
 * spoiler and custom emoji); the rest it refuses.
 */
private val QUOTE_ENTITY_TYPES: Set<TextEntityType> =
    setOf(
        TextEntityType.Bold,
        TextEntityType.Italic,
        TextEntityType.Underline,
        TextEntityType.Strikethrough,
        TextEntityType.Spoiler,
    )

/**
 * How long a quote may be, in UTF-16 code units: Telegram's default `message_reply_quote_length_max` (TDLib's option),
 * which a longer selection is cut to.
 */
internal const val MAX_QUOTE_LENGTH = 1024
