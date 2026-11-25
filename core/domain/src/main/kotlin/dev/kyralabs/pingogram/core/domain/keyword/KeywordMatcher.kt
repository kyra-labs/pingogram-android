package dev.kyralabs.pingogram.core.domain.keyword

import java.text.Normalizer
import java.util.Locale

/** Where [word] matched in a text: its [start] and [end] (exclusive) in the text as written. */
data class KeywordMatch(
    val word: String,
    val start: Int,
    val end: Int,
)

/**
 * Finds the user's keyword alert [words] in texts (board 30, owner decision 2026-11-15): whole words or phrases, any
 * letter case, with or without accents ("cafe" finds "Café"), no patterns. A match must not have a letter or digit just
 * before or after it, so "launch" does not match "relaunched" and "0.9.4" does not match "0.9.45". With
 * [withinUnspacedScripts] (muted words, owner decision 2026-11-25), a word that starts or ends in a script written
 * without spaces (Chinese, Japanese, Thai and the like) matches within the text at that end. Runs on this phone only.
 * Made once per list of words; [matches] is safe from any thread.
 */
class KeywordMatcher(
    words: List<String>,
    private val withinUnspacedScripts: Boolean = false,
) {
    private val needles: List<Pair<String, String>> =
        words
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy(::keyOf)
            .map { it to fold(it).text }

    /** Whether there are no words to look for. */
    val isEmpty: Boolean get() = needles.isEmpty()

    /** Every match in [text], in the text's order; where two words match at one place, the longer one. */
    fun matches(text: String): List<KeywordMatch> {
        if (needles.isEmpty() || text.isEmpty()) return emptyList()
        val folded = fold(text)
        val found =
            needles.flatMap { (word, needle) ->
                generateSequence(folded.text.indexOf(needle)) { from -> folded.text.indexOf(needle, from + 1) }
                    .takeWhile { it >= 0 }
                    .filter { start -> folded.isWholeWord(start, start + needle.length, edgesOf(needle)) }
                    .map { start ->
                        KeywordMatch(word, folded.original(start), folded.originalEnd(start + needle.length))
                    }.toList()
            }
        return found
            .sortedWith(compareBy<KeywordMatch> { it.start }.thenByDescending { it.end - it.start })
            .fold(mutableListOf()) { kept, match ->
                if (kept.lastOrNull()?.let { match.start < it.end } != true) kept += match
                kept
            }
    }

    /** Which ends of [needle] need a word boundary: both, unless [withinUnspacedScripts] frees an unspaced end. */
    private fun edgesOf(needle: String): Edges =
        if (!withinUnspacedScripts) {
            Edges.Both
        } else {
            Edges(
                start = !needle.codePointAt(0).isUnspaced(),
                end = !needle.codePointBefore(needle.length).isUnspaced(),
            )
        }

    /** Whether a match must have no letter or digit just before it ([start]) and just after it ([end]). */
    private data class Edges(
        val start: Boolean,
        val end: Boolean,
    ) {
        companion object {
            val Both = Edges(start = true, end = true)
        }
    }

    /** The first match in [text], if any. */
    fun firstMatch(text: String): KeywordMatch? = matches(text).firstOrNull()

    /** A text folded for comparing ([text]: lower case, accents taken off), with each char's index in the original. */
    private class Folded(
        val text: String,
        private val origins: IntArray,
        private val originalLength: Int,
    ) {
        fun original(index: Int): Int = origins[index]

        fun originalEnd(index: Int): Int = if (index >= origins.size) originalLength else origins[index]

        fun isWholeWord(
            start: Int,
            end: Int,
            edges: Edges,
        ): Boolean {
            val before = text.getOrNull(start - 1)
            val after = text.getOrNull(end)
            return (!edges.start || before == null || !before.isLetterOrDigit()) &&
                (!edges.end || after == null || !after.isLetterOrDigit())
        }
    }

    companion object {
        /** The scripts written without spaces between words. */
        private val UNSPACED_SCRIPTS =
            setOf(
                Character.UnicodeScript.HAN,
                Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA,
                Character.UnicodeScript.THAI,
                Character.UnicodeScript.LAO,
                Character.UnicodeScript.KHMER,
                Character.UnicodeScript.MYANMAR,
            )

        private fun Int.isUnspaced(): Boolean = Character.UnicodeScript.of(this) in UNSPACED_SCRIPTS

        /** What two words are compared by: [word] trimmed, in lower case, without its accents. */
        fun keyOf(word: String): String = fold(word.trim()).text

        /**
         * [value] in lower case without its accents: each char decomposed (NFD) and its combining marks dropped, so the
         * folded text keeps an index into [value] for every char.
         */
        private fun fold(value: String): Folded {
            val text = StringBuilder(value.length)
            val origins = ArrayList<Int>(value.length)
            var index = 0
            while (index < value.length) {
                val codePoint = value.codePointAt(index)
                val size = Character.charCount(codePoint)
                val decomposed = Normalizer.normalize(value.substring(index, index + size), Normalizer.Form.NFD)
                decomposed
                    .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
                    .lowercase(Locale.ROOT)
                    .forEach { char ->
                        text.append(char)
                        origins += index
                    }
                index += size
            }
            return Folded(text.toString(), origins.toIntArray(), value.length)
        }
    }
}
