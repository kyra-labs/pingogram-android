package dev.kyralabs.pingogram.core.ui.component

import java.text.BreakIterator

/**
 * Up to two initials for [name]: the first character of each of its first two words, in title case. Characters are
 * what the user sees as one (grapheme clusters), so an emoji, a flag or a letter with a combining accent stays whole;
 * on Android, ICU also keeps emoji sequences joined with zero-width joiners together.
 *
 * A word starts at its first letter, digit or symbol (emoji included): punctuation and invisible formatting characters
 * before it are skipped, so "(Work) chat" gives "WC". Empty when [name] has no such character.
 */
internal fun avatarInitials(name: String): String {
    val characters = BreakIterator.getCharacterInstance().apply { setText(name) }
    val initials = StringBuilder()
    var count = 0
    var atWordStart = true
    var start = characters.first()
    var end = characters.next()
    while (end != BreakIterator.DONE && count < MAX_INITIALS) {
        val codePoint = name.codePointAt(start)
        if (codePoint.isWordSeparator()) {
            atWordStart = true
        } else if (atWordStart && codePoint.canStartInitial()) {
            initials
                .appendCodePoint(Character.toTitleCase(codePoint))
                .append(name, start + Character.charCount(codePoint), end)
            count++
            atWordStart = false
        }
        start = end
        end = characters.next()
    }
    return initials.toString()
}

private const val MAX_INITIALS = 2

private fun Int.isWordSeparator() = Character.isWhitespace(this) || Character.isSpaceChar(this)

private fun Int.canStartInitial() =
    Character.isLetterOrDigit(this) ||
        when (Character.getType(this).toByte()) {
            Character.LETTER_NUMBER,
            Character.OTHER_NUMBER,
            Character.MATH_SYMBOL,
            Character.CURRENCY_SYMBOL,
            Character.MODIFIER_SYMBOL,
            Character.OTHER_SYMBOL,
            -> true

            else -> false
        }
