package dev.kyralabs.pingogram.feature.chat.ui.bot

import dev.kyralabs.pingogram.core.domain.model.Snippet
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * A snippet's name being typed: "/" and what follows it ([query]) from [start] up to the cursor at [end], where the "/"
 * starts the field or follows a space or a line, so "My address is /addr" finds it and "a/b" or a link does not.
 */
internal data class SlashWord(
    val start: Int,
    val end: Int,
    val query: String,
)

/** The snippet name typed before [cursor] in [text]; null where there is none, or for a selection ([cursor] -1). */
internal fun snippetQuery(
    text: CharSequence,
    cursor: Int,
): SlashWord? {
    val match = if (cursor in 0..text.length) SNIPPET_BEING_TYPED.find(text.subSequence(0, cursor)) else null
    return match?.let { SlashWord(start = it.range.first, end = cursor, query = it.groupValues[1]) }
}

/** The snippets whose names [query] starts, in their order, ignoring case. */
internal fun List<Snippet>.namedFrom(query: String): ImmutableList<Snippet> =
    filter { it.name.startsWith(query, ignoreCase = true) }.toImmutableList()

/** "/" at the start or after white space, then a snippet name's characters up to the end: what the cursor follows. */
private val SNIPPET_BEING_TYPED = Regex("(?<=^|\\s)/([\\p{L}\\p{Nd}_]{0,${Snippet.MAX_NAME_LENGTH}})$")
