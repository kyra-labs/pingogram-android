package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.core.ui.time.UserStatusFormatter
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
 * - a date separator: "Today", "Yesterday", "Tomorrow" (scheduled messages), "Monday, March 17" this year, "March 17,
 *   2024" in other years;
 * - "online" or "last seen" of a user, as `core:ui`'s [UserStatusFormatter] says it, which the profile shares;
 * - the time of a forum topic's last message in the topic list: "9:41 AM" today, "Mar 17" this year, "3/17/24" before.
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
    private val userStatus = UserStatusFormatter(zone, locale, is24HourClock, patterns)

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

            // Scheduled messages go out in the future.
            day == today.plusDays(1) -> UiText.resource(R.string.chat_date_tomorrow)

            day.year == today.year -> UiText.Plain(weekdayAndDate.format(day))

            else -> UiText.Plain(fullDate.format(day))
        }

    /** "online" or when the user with [status] was last online, as of [now] ([UserStatusFormatter]). */
    fun userStatus(
        status: UserStatus,
        now: Instant,
    ): UiText = userStatus.status(status, now)

    /**
     * When [epochSeconds] (Unix time) was, as of [now], in a list (a forum's topics): the time today, the day and month
     * this year, else the date.
     */
    fun listTime(
        epochSeconds: Long,
        now: Instant,
    ): String {
        val dateTime = Instant.ofEpochSecond(epochSeconds).atZone(zone)
        val today = now.atZone(zone).toLocalDate()
        return when {
            !dateTime.toLocalDate().isBefore(today) -> time.format(dateTime)
            dateTime.year == today.year -> dayAndMonth.format(dateTime)
            else -> shortDate.format(dateTime)
        }
    }

    /**
     * When [epochSeconds] (Unix time) was, as of [now], as a day and a time of day ("Seen yesterday at 9:41 AM"):
     * today, yesterday, the day and month this year, else the date, each with the time.
     */
    fun dayAndTime(
        epochSeconds: Long,
        now: Instant,
    ): DayAndTime {
        val dateTime = Instant.ofEpochSecond(epochSeconds).atZone(zone)
        val today = now.atZone(zone).toLocalDate()
        val day = dateTime.toLocalDate()
        val timeOfDay = time.format(dateTime)
        return when {
            !day.isBefore(today) -> DayAndTime.Today(timeOfDay)
            day == today.minusDays(1) -> DayAndTime.Yesterday(timeOfDay)
            day.year == today.year -> DayAndTime.OnDate(dayAndMonth.format(dateTime), timeOfDay)
            else -> DayAndTime.OnDate(shortDate.format(dateTime), timeOfDay)
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
}

/** A moment as [ChatTimeFormatter.dayAndTime] words it: its [time] of day, after its day. */
internal sealed interface DayAndTime {
    val time: String

    data class Today(
        override val time: String,
    ) : DayAndTime

    data class Yesterday(
        override val time: String,
    ) : DayAndTime

    /** Another day: [date] ("Mar 17", or "3/17/24" in another year). */
    data class OnDate(
        val date: String,
        override val time: String,
    ) : DayAndTime
}
