package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chat.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The times of the conversation (`REQUIREMENTS.md` FR-C1, FR-C8, §6.4) in [zone] and [locale], with the platform's
 * patterns ([TimePatterns]):
 * - a message's time: "9:41 AM", or "09:41" on the 24-hour clock ([is24HourClock]);
 * - a date separator: "Today", "Yesterday", "Monday, March 17" this year, "March 17, 2024" before;
 * - "last seen" of a user: "just now", "5 minutes ago", "today at 9:41 AM", "yesterday at 9:41 AM", "Mar 17", or
 *   "3/17/2024" before this year.
 *
 * Built in the ViewModel, so formatters are created once per zone, locale and clock setting. Times in the future (a
 * clock behind Telegram's) count as now. A platform pattern java.time cannot read falls back to java.time's own
 * localized style, as in the chat list.
 */
internal class ChatTimeFormatter(
    val zone: ZoneId,
    val locale: Locale,
    val is24HourClock: Boolean,
    patterns: TimePatterns,
) {
    private val time =
        patterns.formatter(if (is24HourClock) "Hm" else "hm") { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    private val weekdayAndDate =
        patterns.formatter("MMMMEEEEd") { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
    private val fullDate = patterns.formatter("yMMMMd") { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG) }
    private val dayAndMonth = patterns.formatter("MMMd") { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    private val shortDate = patterns.formatter("yMd") { DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT) }

    /** The time of day of [epochSeconds] (Unix time). */
    fun time(epochSeconds: Long): String = time.format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

    /** The day of [epochSeconds] (Unix time) in [zone]. */
    fun day(epochSeconds: Long): LocalDate = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate()

    /** The label of a date separator for [day], as of [today]. */
    fun dateLabel(
        day: LocalDate,
        today: LocalDate,
    ): UiText =
        when {
            day == today -> UiText.resource(R.string.chat_date_today)
            day == today.minusDays(1) -> UiText.resource(R.string.chat_date_yesterday)
            day.year == today.year -> UiText.Plain(weekdayAndDate.format(day))
            else -> UiText.Plain(fullDate.format(day))
        }

    /** "last seen …" for a user last online at [epochSeconds] (Unix time), as of [now]. */
    fun lastSeen(
        epochSeconds: Long,
        now: Instant,
    ): UiText {
        val seconds = (now.epochSecond - epochSeconds).coerceAtLeast(0)
        val dateTime = Instant.ofEpochSecond(epochSeconds).atZone(zone)
        val day = dateTime.toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        return when {
            seconds < SECONDS_PER_MINUTE -> {
                UiText.resource(R.string.chat_subtitle_last_seen_just_now)
            }

            seconds < SECONDS_PER_HOUR -> {
                val minutes = (seconds / SECONDS_PER_MINUTE).toInt()
                UiText.plural(R.plurals.chat_subtitle_last_seen_minutes, minutes, minutes)
            }

            !day.isBefore(today) -> {
                UiText.resource(R.string.chat_subtitle_last_seen_today, time.format(dateTime))
            }

            day == today.minusDays(1) -> {
                UiText.resource(R.string.chat_subtitle_last_seen_yesterday, time.format(dateTime))
            }

            day.year == today.year -> {
                UiText.resource(R.string.chat_subtitle_last_seen_date, dayAndMonth.format(dateTime))
            }

            else -> {
                UiText.resource(R.string.chat_subtitle_last_seen_date, shortDate.format(dateTime))
            }
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
        const val SECONDS_PER_MINUTE = 60L
        const val SECONDS_PER_HOUR = 3_600L
    }
}
