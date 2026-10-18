package dev.kyralabs.pingogram.feature.chats.ui

import java.time.Instant
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * The time of a chat's last message in the chat list (`REQUIREMENTS.md` FR-L2, §6.4), in [zone] and [locale]:
 * - today: the time, "9:41 AM";
 * - the six days before: the weekday, "Mon";
 * - earlier this year: the day and month, "Mar 17";
 * - before: the date, "3/17/25".
 *
 * Built in the ViewModel for every row, so formatters are created once per zone and locale. Times in the future (a
 * clock behind Telegram's) count as today.
 */
internal class ChatListTimeFormatter(
    val zone: ZoneId,
    val locale: Locale,
) {
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    private val dayAndMonth = DateTimeFormatter.ofPattern(dayAndMonthPattern(locale), locale)
    private val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)

    /** The time [epochSeconds] (Unix time) as of [now]. */
    fun format(
        epochSeconds: Long,
        now: Instant,
    ): String {
        val dateTime = Instant.ofEpochSecond(epochSeconds).atZone(zone)
        val day = dateTime.toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        return when {
            !day.isBefore(today) -> time.format(dateTime)
            day.isAfter(today.minusDays(DAYS_PER_WEEK)) -> day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            day.year == today.year -> dayAndMonth.format(dateTime)
            else -> date.format(dateTime)
        }
    }

    private companion object {
        const val DAYS_PER_WEEK = 7L
    }
}

/**
 * The [locale]'s medium date pattern without the year, "MMM d" for English: java.time has no pattern for a day and
 * month alone (Android's `DateFormat.getBestDateTimePattern` has, but would keep this code off the JVM).
 */
internal fun dayAndMonthPattern(locale: Locale): String =
    withoutYear(
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.MEDIUM, null, IsoChronology.INSTANCE, locale),
    )

/**
 * [pattern] without its year and the separators and literals that only belong to it: "MMM d, y" → "MMM d",
 * "d MMM y 'г'." → "d MMM", "y/MM/dd" → "MM/dd", "dd.MM.y" → "dd.MM".
 */
internal fun withoutYear(pattern: String): String {
    val tokens = patternTokens(pattern)
    val year = tokens.indexOfFirst { it.isField && it.text.first() in YEAR_LETTERS }
    if (year < 0) return pattern
    val previousField = (year - 1 downTo 0).firstOrNull { tokens[it].isField }
    val nextField = (year + 1 until tokens.size).firstOrNull { tokens[it].isField }
    val removed =
        when {
            // First: the year and the separator after it ("y/MM/dd").
            previousField == null -> year until (nextField ?: tokens.size)

            // Last: the separator before it and anything after it ("MMM d, y", "d MMM y 'г'.").
            nextField == null -> previousField + 1 until tokens.size

            // Between fields: the separator before it, keeping the one after.
            else -> previousField + 1..year
        }
    return tokens.filterIndexed { index, _ -> index !in removed }.joinToString("") { it.text }.trim()
}

/** A run of one pattern letter (a field), or literal text: separators and quoted text with its quotes. */
private class PatternToken(
    val text: String,
    val isField: Boolean,
)

private fun patternTokens(pattern: String): List<PatternToken> {
    val tokens = mutableListOf<PatternToken>()
    var index = 0
    while (index < pattern.length) {
        val char = pattern[index]
        val end =
            when {
                char == '\'' -> quotedEnd(pattern, index)
                char.isAsciiLetter() -> runEnd(pattern, index) { it == char }
                else -> runEnd(pattern, index) { it != '\'' && !it.isAsciiLetter() }
            }
        tokens += PatternToken(pattern.substring(index, end), isField = char.isAsciiLetter())
        index = end
    }
    return tokens
}

/** The end of the quoted text starting at [start]; `''` inside it is an escaped quote. */
private fun quotedEnd(
    pattern: String,
    start: Int,
): Int {
    var index = start + 1
    while (index < pattern.length) {
        if (pattern[index] == '\'') {
            if (index + 1 < pattern.length && pattern[index + 1] == '\'') index++ else return index + 1
        }
        index++
    }
    return pattern.length
}

private inline fun runEnd(
    pattern: String,
    start: Int,
    belongs: (Char) -> Boolean,
): Int {
    var index = start + 1
    while (index < pattern.length && belongs(pattern[index])) index++
    return index
}

private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

private const val YEAR_LETTERS = "yuY"
