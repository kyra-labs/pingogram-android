package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The words of a search query ([terms]) that a result's text shows highlighted (`REQUIREMENTS.md` FR-L6): each word the
 * query has, wherever the text contains it, without regard to case. The row applies it to its text as resolved, since
 * a message's preview may come from resources ("Photo · …").
 */
@Immutable
data class MatchHighlight(
    val terms: ImmutableList<String>,
) {
    /** Where [text] contains a term, in order, overlapping matches merged; empty without any. */
    fun ranges(text: String): List<IntRange> {
        val matches =
            terms
                .flatMap { term ->
                    generateSequence(text.indexOf(term, ignoreCase = true).takeIf { it >= 0 }) { start ->
                        text.indexOf(term, start + 1, ignoreCase = true).takeIf { it >= 0 }
                    }.map { start -> start until start + term.length }
                        .toList()
                }.sortedBy { it.first }
        return matches.fold(mutableListOf()) { merged, range ->
            val last = merged.lastOrNull()
            if (last != null && range.first <= last.last + 1) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
            merged
        }
    }

    /** Where in [text] the first match starts; null without any. */
    fun firstMatch(text: String): Int? =
        terms.mapNotNull { term -> text.indexOf(term, ignoreCase = true).takeIf { it >= 0 } }.minOrNull()

    override fun toString() = "MatchHighlight(${terms.size} terms)"

    companion object {
        /** Highlights nothing. */
        val None = MatchHighlight(persistentListOf())

        /** The words of [query], split at whitespace, each once. */
        fun of(query: String): MatchHighlight =
            MatchHighlight(
                query
                    .split(WHITESPACE)
                    .filter { it.isNotEmpty() }
                    .distinctBy { it.lowercase() }
                    .toImmutableList(),
            )

        private val WHITESPACE = Regex("""\s+""")
    }
}

/** [label] ("Ada:") in [labelColor], then [text] with every match of [highlight] in [match]. */
internal fun highlighted(
    label: String?,
    text: String,
    highlight: MatchHighlight,
    labelColor: Color,
    match: SpanStyle,
): AnnotatedString =
    buildAnnotatedString {
        if (label != null) {
            withStyle(SpanStyle(color = labelColor)) { append(label) }
            append(' ')
        }
        val start = length
        append(text)
        highlight.ranges(text).forEach { range -> addStyle(match, start + range.first, start + range.last + 1) }
    }
