package dev.kyralabs.pingogram.feature.settings.ui

import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/**
 * The times of the settings pages (`REQUIREMENTS.md` §6.4) in [zone] and [locale], with the platform's patterns
 * ([TimePatterns]), as the chat list shows its times:
 * - when a session was last active: today the time, "9:41 AM" ("09:41" on the 24-hour clock, [is24HourClock]); the
 *   six days before the weekday, "Mon"; earlier this year the day and month, "Mar 17"; before that the date,
 *   "3/17/2025";
 * - a moment in full, the date and the time, "Mar 17, 2026, 9:41 AM";
 * - when something ends: today the time, "6:30 PM", later the date and the time.
 *
 * View models build it through [SettingsTimeFormats], once per zone, language and clock setting. Times in the future
 * (a clock behind Telegram's) count as today. A platform pattern java.time cannot read falls back to java.time's own
 * localized style, as in the chat list.
 */
internal class SettingsTimeFormatter(
    val zone: ZoneId,
    val locale: Locale,
    val is24HourClock: Boolean,
    patterns: TimePatterns,
) {
    private val time =
        patterns.formatter(if (is24HourClock) "Hm" else "hm") { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    private val dayAndMonth = patterns.formatter("MMMd") { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    private val date = patterns.formatter("yMd") { DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT) }
    private val dateTime =
        patterns.formatter(if (is24HourClock) "yMMMdHm" else "yMMMdhm") {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        }

    /** When something happened at [epochSeconds] (Unix time), as of [now], as briefly as the chat list says it. */
    fun lastActive(
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

    /** The date and time of [epochSeconds] (Unix time). */
    fun dateTime(epochSeconds: Long): String = dateTime.format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

    /** When something that lasts ends at [epochSeconds] (Unix time), as of [now]: the time if today, else in full. */
    fun until(
        epochSeconds: Long,
        now: Instant,
    ): String {
        val end = Instant.ofEpochSecond(epochSeconds).atZone(zone)
        return if (end.toLocalDate() == now.atZone(zone).toLocalDate()) time.format(end) else dateTime(epochSeconds)
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

/**
 * The clock and the device's time settings that settings view models format times with: [now] and a
 * [SettingsTimeFormatter] for the current time zone, language and clock setting, which can change while the app runs.
 * Tests pass a clock, fixed patterns and a language.
 */
class SettingsTimeFormats internal constructor(
    private val clock: WallClock,
    private val patterns: TimePatterns,
    private val locale: () -> Locale,
) {
    @Inject
    constructor(clock: WallClock, patterns: TimePatterns) : this(clock, patterns, Locale::getDefault)

    private var formatter: SettingsTimeFormatter? = null

    fun now(): Instant = clock.now()

    internal fun formatter(): SettingsTimeFormatter {
        val zone = clock.zone()
        val locale = locale()
        val is24HourClock = patterns.is24HourClock()
        return formatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: SettingsTimeFormatter(zone, locale, is24HourClock, patterns).also { formatter = it }
    }
}
