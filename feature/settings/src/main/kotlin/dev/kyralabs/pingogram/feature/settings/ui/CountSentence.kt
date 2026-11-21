package dev.kyralabs.pingogram.feature.settings.ui

/**
 * A sentence split around the count it holds, so a screen can draw the count large between the words before and
 * after it, wherever a language puts it. Plurals always include the count (`docs/TRANSLATING.md`), which some
 * languages' "one" form needs, as it covers more numbers than 1.
 *
 * @property before the words before the count, trimmed; empty when the sentence starts with it.
 * @property count the count as the sentence formats it.
 * @property after the words after the count, trimmed; the whole sentence when it holds no count.
 */
internal data class CountSentence(
    val before: String,
    val count: String,
    val after: String,
)

/** [sentence] split at the first [count] in it, as the sentence formats it; all of it after the count if none. */
internal fun splitAtCount(
    sentence: String,
    count: String,
): CountSentence {
    val at = sentence.indexOf(count)
    return if (at < 0) {
        CountSentence(before = "", count = count, after = sentence.trim())
    } else {
        CountSentence(
            before = sentence.substring(0, at).trim(),
            count = count,
            after = sentence.substring(at + count.length).trim(),
        )
    }
}
