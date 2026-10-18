package dev.kyralabs.pingogram.feature.chats.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * The time of a chat's last message in the chat list (`REQUIREMENTS.md` FR-L2, §6.4), in [zone] and [locale], with
 * the platform's patterns ([TimePatterns]):
 * - today: the time, "9:41 AM", or "09:41" on the 24-hour clock ([is24HourClock]);
 * - the six days before: the weekday, "Mon";
 * - earlier this year: the day and month, "Mar 17";
 * - before: the date, "3/17/2025".
 *
 * Built in the ViewModel for every row, so formatters are created once per zone, locale and clock setting. Times in
 * the future (a clock behind Telegram's) count as today. A platform pattern java.time cannot read (ICU's day periods,
 * which some languages use and older java.time versions lack) falls back to java.time's own localized style.
 */
internal class ChatListTimeFormatter(
    val zone: ZoneId,
    val locale: Locale,
    val is24HourClock: Boolean,
    patterns: TimePatterns,
) {
    private val time =
        patterns.formatter(if (is24HourClock) "Hm" else "hm") { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    private val dayAndMonth = patterns.formatter("MMMd") { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    private val date = patterns.formatter("yMd") { DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT) }

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

    /** Whether this formatter is the one for [zone], [locale] and the clock setting. */
    fun isFor(
        zone: ZoneId,
        locale: Locale,
        is24HourClock: Boolean,
    ): Boolean = this.zone == zone && this.locale == locale && this.is24HourClock == is24HourClock

    /** The platform's pattern for [skeleton], or [fallback] if java.time cannot read it. */
    private fun TimePatterns.formatter(
        skeleton: String,
        fallback: () -> DateTimeFormatter,
    ): DateTimeFormatter =
        try {
            DateTimeFormatter.ofPattern(bestPattern(locale, skeleton), locale)
        } catch (_: IllegalArgumentException) {
            fallback().withLocale(locale)
        }

    private companion object {
        const val DAYS_PER_WEEK = 7L
    }
}
