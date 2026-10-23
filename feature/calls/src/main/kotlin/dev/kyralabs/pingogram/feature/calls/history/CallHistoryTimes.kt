package dev.kyralabs.pingogram.feature.calls.history

import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.calls.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The times of Recent calls in [zone] and [locale], with the platform's patterns ([TimePatterns]), as the
 * conversation's date separators word them: a call's time of day ("9:41 AM", "09:41" on the 24-hour clock) and its
 * day's header ("Today", "Yesterday", "Monday, March 17" this year, "March 17, 2024" before). Built in the ViewModel,
 * once per zone, language and clock setting; a pattern java.time cannot read falls back to its own localized style.
 */
internal class CallHistoryTimes(
    private val zone: ZoneId,
    private val locale: Locale,
    private val is24HourClock: Boolean,
    patterns: TimePatterns,
) {
    private val time =
        patterns.formatter(if (is24HourClock) "Hm" else "hm") { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    private val weekdayAndDate =
        patterns.formatter("MMMMEEEEd") { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
    private val fullDate = patterns.formatter("yMMMMd") { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG) }

    /** The time of day of [epochSeconds]. */
    fun time(epochSeconds: Long): String = time.format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

    /** The day of [epochSeconds] in [zone]. */
    fun day(epochSeconds: Long): LocalDate = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate()

    /** Today in [zone] at [now]. */
    fun today(now: Instant): LocalDate = now.atZone(zone).toLocalDate()

    /** The header of [day], as of [today]. */
    fun dayLabel(
        day: LocalDate,
        today: LocalDate,
    ): UiText =
        when {
            day == today -> UiText.resource(R.string.recent_calls_today)
            day == today.minusDays(1) -> UiText.resource(R.string.recent_calls_yesterday)
            day.year == today.year -> UiText.Plain(weekdayAndDate.format(day))
            else -> UiText.Plain(fullDate.format(day))
        }

    /** Whether these are the times of [zone], [locale] and the clock setting. */
    fun isFor(
        zone: ZoneId,
        locale: Locale,
        is24HourClock: Boolean,
    ): Boolean = this.zone == zone && this.locale == locale && this.is24HourClock == is24HourClock

    private fun TimePatterns.formatter(
        skeleton: String,
        fallback: () -> DateTimeFormatter,
    ): DateTimeFormatter =
        try {
            DateTimeFormatter.ofPattern(bestPattern(locale, skeleton), locale)
        } catch (_: IllegalArgumentException) {
            fallback().withLocale(locale)
        }
}
