package dev.kyralabs.pingogram.core.ui.time

import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * When a message reminder rings (the owner's board 29), in words for [zone], [locale] and the device's clock, with the
 * platform's patterns: the time alone today ("11:30 PM"), the weekday and the time within a week ("Sun, 9:00 AM"),
 * else the date too ("Mon, Nov 23, 9:00 AM"), a day and a time joined in the language's way
 * (`reminder_label_day_time`). [day] says the day alone, for a reminder done: "Today", the weekday within a week back
 * or ahead, else the date.
 */
class ReminderTimeLabels(
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

    /** [at] as of [now]: its day where it is not today, and its time. */
    fun label(
        at: Instant,
        now: Instant,
    ): UiText {
        val moment = at.atZone(zone)
        val days = ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), moment.toLocalDate())
        val timeOfDay = time.format(moment)
        return when {
            days == 0L -> UiText.Plain(timeOfDay)
            days in 1 until WEEK -> UiText.resource(R.string.reminder_label_day_time, weekday.format(moment), timeOfDay)
            else -> UiText.resource(R.string.reminder_label_day_time, date.format(moment), timeOfDay)
        }
    }

    /** The day of [at] as of [now], without its time: "Today", the weekday within a week, else the date. */
    fun day(
        at: Instant,
        now: Instant,
    ): UiText {
        val moment = at.atZone(zone)
        val days = ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), moment.toLocalDate())
        return when {
            days == 0L -> UiText.resource(R.string.reminder_label_today)
            days in -WEEK + 1 until WEEK -> UiText.Plain(weekday.format(moment))
            else -> UiText.Plain(date.format(moment))
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
