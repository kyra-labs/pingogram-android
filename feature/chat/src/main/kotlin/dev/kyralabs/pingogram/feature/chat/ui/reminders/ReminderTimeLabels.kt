package dev.kyralabs.pingogram.feature.chat.ui.reminders

import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chat.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * When a reminder rings, in words for [zone], [locale] and the device's clock, with the platform's patterns: the time
 * alone today ("11:30 PM"), the weekday and the time within a week ("Sun, 9:00 AM"), else the date too ("Mon, Nov 23,
 * 9:00 AM"), a day and a time joined in the language's way (`reminder_day_time`).
 */
internal class ReminderTimeLabels(
    private val zone: ZoneId,
    private val locale: Locale,
    private val is24HourClock: Boolean,
    patterns: TimePatterns,
) {
    private val time =
        patterns.formatter(locale, if (is24HourClock) "Hm" else "hm") {
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        }
    private val weekday = patterns.formatter(locale, "EEE") { DateTimeFormatter.ofPattern("EEE") }
    private val date = patterns.formatter(locale, "EEEMMMd") { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    /** [at] as of [now]. */
    fun label(
        at: Instant,
        now: Instant,
    ): UiText {
        val moment = at.atZone(zone)
        val today = now.atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(today, moment.toLocalDate())
        val timeOfDay = time.format(moment)
        return when {
            days == 0L -> UiText.Plain(timeOfDay)
            days in 1 until WEEK -> UiText.resource(R.string.reminder_day_time, weekday.format(moment), timeOfDay)
            else -> UiText.resource(R.string.reminder_day_time, date.format(moment), timeOfDay)
        }
    }

    /** Whether these are the labels for [zone], [locale] and the clock setting. */
    fun isFor(
        zone: ZoneId,
        locale: Locale,
        is24HourClock: Boolean,
    ): Boolean = this.zone == zone && this.locale == locale && this.is24HourClock == is24HourClock

    private companion object {
        const val WEEK = 7L

        /** The platform's pattern for [skeleton], or [fallback] if java.time cannot read it. */
        fun TimePatterns.formatter(
            locale: Locale,
            skeleton: String,
            fallback: () -> DateTimeFormatter,
        ): DateTimeFormatter =
            try {
                DateTimeFormatter.ofPattern(bestPattern(locale, skeleton), locale)
            } catch (_: IllegalArgumentException) {
                fallback().withLocale(locale)
            }
    }
}
