package dev.kyralabs.pingogram.core.notifications

import dev.kyralabs.pingogram.core.domain.model.BirthdayReminder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZoneId

/** When birthday reminders ring: in the morning of the day, local time. */
internal val BIRTHDAY_RING_TIME: LocalTime = LocalTime.of(9, 0)

/**
 * The next time [reminder] rings after [now] in [zone]: [BIRTHDAY_RING_TIME] on its day this year, or next year once
 * that has passed. A birthday on 29 February rings on the 28th in other years.
 */
internal fun nextBirthdayRing(
    reminder: BirthdayReminder,
    now: Instant,
    zone: ZoneId,
): Instant {
    val monthDay = MonthDay.of(reminder.month, reminder.day)
    val today = now.atZone(zone).toLocalDate()
    val thisYear = monthDay.ringIn(today.year, zone)
    return if (thisYear.isAfter(now)) thisYear else monthDay.ringIn(today.year + 1, zone)
}

/** [BIRTHDAY_RING_TIME] on this day of [year] in [zone]. */
private fun MonthDay.ringIn(
    year: Int,
    zone: ZoneId,
): Instant = atYear(year).atTime(BIRTHDAY_RING_TIME).atZone(zone).toInstant()

/** Whether [date] is [reminder]'s day, the 28th of February standing in for the 29th in other years. */
internal fun isBirthdayOf(
    reminder: BirthdayReminder,
    date: LocalDate,
): Boolean = MonthDay.of(reminder.month, reminder.day).atYear(date.year) == date
