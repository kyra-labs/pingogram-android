package dev.kyralabs.pingogram.core.domain.model

import java.util.Locale

/**
 * Whether this name has a word starting with each word of [query], ignoring case; true for a blank query. How search
 * narrows the top people and finds a chat by its name on this phone as the user types.
 */
fun String.matchesWords(
    query: String,
    locale: Locale,
): Boolean {
    val typed = query.lowercase(locale).split(WHITESPACE).filter { it.isNotEmpty() }
    val words = lowercase(locale).split(WHITESPACE)
    return typed.all { word -> words.any { it.startsWith(word) } }
}

private val WHITESPACE = Regex("\\s+")
