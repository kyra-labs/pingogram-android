package dev.kyralabs.pingogram.feature.chat.ui

// Whether a text is only a few emoji, which the conversation shows large and without a bubble (docs/DESIGN_SYSTEM.md
// §4). The emoji are told apart by the sequences of UTS #51 (https://unicode.org/reports/tr51/): flags, keycaps, tag
// sequences (subdivision flags) and zero-width-joiner sequences of pictographs with variation selectors and skin
// tones. Plain Kotlin, without ICU, so it runs the same in JVM tests: JDK 17's BreakIterator does not segment ZWJ
// sequences.

/** The most emoji a text may have to show large. */
internal const val MAX_LARGE_EMOJI = 3

/**
 * Whether [text] is 1 to [MAX_LARGE_EMOJI] emoji and nothing else but whitespace between them: no letters, digits,
 * punctuation or other symbols. A flag, a keycap, a family or a skin-toned emoji counts as one.
 */
internal fun isEmojiOnly(text: String): Boolean {
    var count = 0
    var index = 0
    while (index < text.length && count <= MAX_LARGE_EMOJI) {
        val codePoint = text.codePointAt(index)
        if (Character.isWhitespace(codePoint)) {
            index += Character.charCount(codePoint)
        } else {
            val end = emojiEnd(text, index)
            index = end ?: text.length
            count = if (end == null) NOT_EMOJI else count + 1
        }
    }
    return count in 1..MAX_LARGE_EMOJI
}

/** Where the emoji that starts at [start] ends; null if no emoji starts there. */
private fun emojiEnd(
    text: String,
    start: Int,
): Int? {
    val first = text.codePointAt(start)
    val afterFirst = start + Character.charCount(first)
    return when {
        // A flag: two regional indicators.
        first in REGIONAL_INDICATOR_FIRST..REGIONAL_INDICATOR_LAST -> {
            val second = text.codePointAtOrNull(afterFirst)
            if (second in
                REGIONAL_INDICATOR_FIRST..REGIONAL_INDICATOR_LAST
            ) {
                afterFirst + REGIONAL_INDICATOR_LENGTH
            } else {
                null
            }
        }

        first in '0'.code..'9'.code || first == '#'.code || first == '*'.code -> {
            keycapEnd(text, afterFirst)
        }

        else -> {
            zwjSequenceEnd(text, start)
        }
    }
}

/** A keycap: a digit, `#` or `*`, an optional emoji variation selector, and the combining keycap. */
private fun keycapEnd(
    text: String,
    afterBase: Int,
): Int? {
    val afterSelector = if (text.codePointAtOrNull(afterBase) == VARIATION_SELECTOR_EMOJI) afterBase + 1 else afterBase
    return if (text.codePointAtOrNull(afterSelector) == COMBINING_KEYCAP) afterSelector + 1 else null
}

/** Pictographs joined by zero-width joiners, each with its variation selector, skin tone or tags. */
private fun zwjSequenceEnd(
    text: String,
    start: Int,
): Int? {
    var end = elementEnd(text, start)
    while (end != null && text.codePointAtOrNull(end) == ZERO_WIDTH_JOINER) end = elementEnd(text, end + 1)
    return end
}

/**
 * One pictograph with what may follow it ([modifiersEnd]); an emoji variation selector after it. A text-presentation
 * symbol counts only with the variation selector.
 */
private fun elementEnd(
    text: String,
    start: Int,
): Int? {
    val base = text.codePointAtOrNull(start) ?: return null
    val afterBase = start + Character.charCount(base)
    val selected = text.codePointAtOrNull(afterBase) == VARIATION_SELECTOR_EMOJI
    val isEmoji = base.isEmojiPresentation() || (selected && base.isTextPresentationEmoji())
    return if (isEmoji) modifiersEnd(text, if (selected) afterBase + 1 else afterBase) else null
}

/**
 * Past what follows a pictograph at [index]: a skin tone, or the tags of a subdivision flag with their cancel tag (null
 * if they do not end with one); nothing else.
 */
private fun modifiersEnd(
    text: String,
    index: Int,
): Int? {
    val next = text.codePointAtOrNull(index)
    return when {
        next == null -> index
        next in SKIN_TONE_FIRST..SKIN_TONE_LAST -> index + Character.charCount(next)
        next in TAG_FIRST..TAG_LAST -> tagsEnd(text, index)
        else -> index
    }
}

/** Past the tags from [start] and the cancel tag that ends them; null without one. */
private fun tagsEnd(
    text: String,
    start: Int,
): Int? {
    var index = start
    while (text.codePointAtOrNull(index)?.let { it in TAG_FIRST..TAG_LAST } == true) {
        index += Character.charCount(text.codePointAt(index))
    }
    return if (text.codePointAtOrNull(index) == CANCEL_TAG) index + Character.charCount(CANCEL_TAG) else null
}

private fun String.codePointAtOrNull(index: Int): Int? = if (index < length) codePointAt(index) else null

/**
 * A pictograph shown as an emoji by default: the supplementary emoji blocks (but regional indicators, which pair into
 * flags), the miscellaneous symbols and dingbats, which keyboards send as emoji, and the other symbols of the Basic
 * Multilingual Plane with default emoji presentation.
 */
private fun Int.isEmojiPresentation(): Boolean =
    (
        this in SUPPLEMENTARY_EMOJI_FIRST..SUPPLEMENTARY_EMOJI_LAST &&
            this !in REGIONAL_INDICATOR_FIRST..REGIONAL_INDICATOR_LAST
    ) ||
        this in SYMBOLS_FIRST..DINGBATS_LAST ||
        (this <= Char.MAX_VALUE.code && toChar() in EMOJI_PRESENTATION_SYMBOLS)

/** A symbol that shows as text unless an emoji variation selector follows it: arrows, ©, ®, ™, ‼, ▶ and the like. */
private fun Int.isTextPresentationEmoji(): Boolean =
    this in ARROWS_FIRST..ARROWS_LAST ||
        this in TECHNICAL_FIRST..TECHNICAL_LAST ||
        this in GEOMETRIC_FIRST..GEOMETRIC_LAST ||
        this in SUPPLEMENTAL_ARROWS_FIRST..SUPPLEMENTAL_ARROWS_LAST ||
        this in MISCELLANEOUS_ARROWS_FIRST..MISCELLANEOUS_ARROWS_LAST ||
        (this <= Char.MAX_VALUE.code && toChar() in TEXT_PRESENTATION_SYMBOLS)

/** Symbols outside the ranges of [isEmojiPresentation] that are emoji by default. */
private const val EMOJI_PRESENTATION_SYMBOLS = "⌚⌛⏩⏪⏫⏬⏰⏳◽◾⬛⬜⭐⭕"

/** Single symbols outside the ranges of [isTextPresentationEmoji] that are emoji with a variation selector. */
private const val TEXT_PRESENTATION_SYMBOLS = "©®‼⁉™ℹⓂ〰〽㊗㊙"

/** What [isEmojiOnly] counts once something that is no emoji came up. */
private const val NOT_EMOJI = Int.MAX_VALUE

private const val VARIATION_SELECTOR_EMOJI = 0xFE0F
private const val ZERO_WIDTH_JOINER = 0x200D
private const val COMBINING_KEYCAP = 0x20E3
private const val CANCEL_TAG = 0xE007F
private const val TAG_FIRST = 0xE0020
private const val TAG_LAST = 0xE007E
private const val SKIN_TONE_FIRST = 0x1F3FB
private const val SKIN_TONE_LAST = 0x1F3FF
private const val REGIONAL_INDICATOR_FIRST = 0x1F1E6
private const val REGIONAL_INDICATOR_LAST = 0x1F1FF

/** A regional indicator's length in UTF-16 chars: it is a supplementary character. */
private const val REGIONAL_INDICATOR_LENGTH = 2
private const val SUPPLEMENTARY_EMOJI_FIRST = 0x1F000
private const val SUPPLEMENTARY_EMOJI_LAST = 0x1FAFF
private const val SYMBOLS_FIRST = 0x2600
private const val DINGBATS_LAST = 0x27BF
private const val ARROWS_FIRST = 0x2190
private const val ARROWS_LAST = 0x21FF
private const val TECHNICAL_FIRST = 0x2300
private const val TECHNICAL_LAST = 0x23FF
private const val GEOMETRIC_FIRST = 0x25A0
private const val GEOMETRIC_LAST = 0x25FF
private const val SUPPLEMENTAL_ARROWS_FIRST = 0x2900
private const val SUPPLEMENTAL_ARROWS_LAST = 0x297F
private const val MISCELLANEOUS_ARROWS_FIRST = 0x2B00
private const val MISCELLANEOUS_ARROWS_LAST = 0x2BFF
