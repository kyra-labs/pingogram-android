package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.FoundEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.time.temporal.ChronoField
import java.util.Locale

// When a date or time the text classifier found starts (`REQUIREMENTS.md` FR-X3): Android's classifier tells where a
// date is in a text, not which moment it means, so the found words are read here as [locale] writes dates and times.
// What they do not say comes from when the message was written: the year of "20 November", the day of "17:30". Words
// such as "tomorrow evening" read as no moment: the calendar opens without a start, for the user to set.

/** The event [text] (a date or time the classifier found) names, read in [locale], counting from [writtenAt]. */
internal fun foundEventOf(
    text: String,
    locale: Locale,
    writtenAt: ZonedDateTime,
): FoundEvent {
    val words = text.trim().replace(SPACES, " ")
    val zone = writtenAt.zone
    val dateTime = dateTimeFormats(locale).firstRead { LocalDateTime.parse(words, it) }
    val date = dateFormats(locale, writtenAt.year).firstRead { LocalDate.parse(words, it) }
    val time = timeFormats(locale).firstRead { LocalTime.parse(words, it) }
    return when {
        dateTime != null -> {
            FoundEvent(dateTime.atZone(zone).toInstant().toEpochMilli(), isAllDay = false)
        }

        date != null -> {
            FoundEvent(date.atStartOfDay(zone).toInstant().toEpochMilli(), isAllDay = true)
        }

        time != null -> {
            FoundEvent(
                writtenAt
                    .toLocalDate()
                    .atTime(time)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli(),
                false,
            )
        }

        else -> {
            FoundEvent(startMillis = null, isAllDay = false)
        }
    }
}

/** What [read] reads with the first of these formats the words are in; null where they are in none. */
private fun <T> List<DateTimeFormatter>.firstRead(read: (DateTimeFormatter) -> T): T? =
    firstNotNullOfOrNull { format ->
        try {
            read(format)
        } catch (_: DateTimeParseException) {
            null
        }
    }

private fun dateTimeFormats(locale: Locale): List<DateTimeFormatter> =
    listOf(FormatStyle.SHORT, FormatStyle.MEDIUM, FormatStyle.LONG).map { localized(locale, it, FormatStyle.SHORT) } +
        DateTimeFormatter.ISO_LOCAL_DATE_TIME

private fun dateFormats(
    locale: Locale,
    year: Int,
): List<DateTimeFormatter> =
    FormatStyle.entries.map { localized(locale, it, null) } +
        DateTimeFormatter.ISO_LOCAL_DATE +
        WITHOUT_YEAR.map { pattern(it, locale) { parseDefaulting(ChronoField.YEAR, year.toLong()) } }

private fun timeFormats(locale: Locale): List<DateTimeFormatter> =
    listOf(localized(locale, null, FormatStyle.SHORT)) + TIMES.map { pattern(it, locale) }

private fun localized(
    locale: Locale,
    date: FormatStyle?,
    time: FormatStyle?,
): DateTimeFormatter = DateTimeFormatterBuilder().parseCaseInsensitive().appendLocalized(date, time).toFormatter(locale)

private fun pattern(
    pattern: String,
    locale: Locale,
    defaults: DateTimeFormatterBuilder.() -> Unit = {},
): DateTimeFormatter =
    DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern(pattern)
        .apply(defaults)
        .toFormatter(locale)

/** Days written without their year, which the message's year completes. */
private val WITHOUT_YEAR = listOf("d MMMM", "MMMM d", "d MMM", "MMM d")

/** Times as people type them, besides the locale's own format. */
private val TIMES = listOf("H:mm", "h:mm a", "h:mma", "h a")

/** Narrow and non-breaking spaces, which some locales' formats put before "PM", as typed text has plain ones. */
private val SPACES = Regex("[\\u00A0\\u202F]")
