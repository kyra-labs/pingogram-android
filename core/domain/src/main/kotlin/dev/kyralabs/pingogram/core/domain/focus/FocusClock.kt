package dev.kyralabs.pingogram.core.domain.focus

import dev.kyralabs.pingogram.core.domain.model.ActiveFocus
import dev.kyralabs.pingogram.core.domain.model.FocusProfile
import dev.kyralabs.pingogram.core.domain.model.FocusSchedule
import dev.kyralabs.pingogram.core.domain.model.FocusUntil
import dev.kyralabs.pingogram.core.domain.model.ManualFocus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Which Focus profile is on at a moment, worked out from the clock whenever it is asked (board 31), so nothing needs an
 * alarm: notifications ask as they are rendered, the Quick Settings tile as it is drawn.
 *
 * - A profile turned on (or Focus turned off) by hand wins until it ends: at its time, when the user turns it off, or
 *   at the schedule's next change after it was set (owner decision 2026-11-15).
 * - Otherwise the first profile, in their order, whose schedule covers the moment. A range across midnight covers the
 *   evening of the days it starts on and the morning after.
 */
object FocusClock {
    fun active(
        profiles: List<FocusProfile>,
        manual: ManualFocus?,
        now: Instant,
        zone: ZoneId,
    ): ActiveFocus? {
        val byHand = manual?.takeIf { focus -> endOf(focus, profiles, zone).let { it == null || now.epochSecond < it } }
        return if (byHand != null) {
            byHand.profileId
                ?.let { id -> profiles.firstOrNull { it.id == id } }
                ?.let { ActiveFocus(it, byHand = true, endsAt = endOf(byHand, profiles, zone)) }
        } else {
            val local = now.atZone(zone)
            profiles
                .firstOrNull { it.schedule?.covers(local) == true }
                ?.let { ActiveFocus(it, byHand = false, endsAt = it.schedule?.endAfter(local)) }
        }
    }

    /**
     * When Focus set by hand ends (Unix seconds), or null while it lasts until turned off: at its time, or at the
     * schedule's next change after it was set.
     */
    fun endOf(
        manual: ManualFocus,
        profiles: List<FocusProfile>,
        zone: ZoneId,
    ): Long? =
        when (val until = manual.until) {
            is FocusUntil.Time -> until.epochSeconds
            FocusUntil.TurnedOff -> null
            FocusUntil.ScheduleChange -> nextChange(profiles, manual.setAt, zone)
        }

    /** The first moment after [afterSeconds] when any schedule starts or ends; null without schedules. */
    fun nextChange(
        profiles: List<FocusProfile>,
        afterSeconds: Long,
        zone: ZoneId,
    ): Long? {
        val start =
            Instant
                .ofEpochSecond(afterSeconds)
                .atZone(zone)
                .toLocalDate()
                .minusDays(1)
        return profiles
            .mapNotNull { it.schedule }
            .flatMap { schedule -> (0..SEARCH_DAYS).flatMap { schedule.edgesOn(start.plusDays(it.toLong()), zone) } }
            .filter { it > afterSeconds }
            .minOrNull()
    }

    /** When [schedule] next starts after [now] (Unix seconds); null if it runs on no day. */
    fun nextStart(
        schedule: FocusSchedule,
        now: Instant,
        zone: ZoneId,
    ): Long? {
        val today = now.atZone(zone).toLocalDate()
        return (0..SEARCH_DAYS)
            .mapNotNull { offset ->
                schedule.edgesOn(today.plusDays(offset.toLong()), zone).firstOrNull()
            }.firstOrNull { it > now.epochSecond }
    }

    private fun FocusSchedule.covers(local: ZonedDateTime): Boolean {
        val minute = local.hour * MINUTES_PER_HOUR + local.minute
        val today = local.dayOfWeek
        return if (endsNextDay) {
            (today in days && minute >= startMinute) || (today.minus(1) in days && minute < endMinute)
        } else {
            today in days && minute >= startMinute && minute < endMinute
        }
    }

    /** When the range covering [local] ends, in Unix seconds. */
    private fun FocusSchedule.endAfter(local: ZonedDateTime): Long {
        val minute = local.hour * MINUTES_PER_HOUR + local.minute
        val endDay =
            when {
                !endsNextDay -> local.toLocalDate()
                minute >= startMinute -> local.toLocalDate().plusDays(1)
                else -> local.toLocalDate()
            }
        return endDay.atStartOfDay(local.zone).plusMinutes(endMinute.toLong()).toEpochSecond()
    }

    /** The moments a range starting on [date] starts and ends, if it runs that day. */
    private fun FocusSchedule.edgesOn(
        date: LocalDate,
        zone: ZoneId,
    ): List<Long> {
        if (date.dayOfWeek !in days) return emptyList()
        val midnight = date.atStartOfDay(zone)
        val end = (if (endsNextDay) midnight.plusDays(1) else midnight).plusMinutes(endMinute.toLong())
        return listOf(midnight.plusMinutes(startMinute.toLong()).toEpochSecond(), end.toEpochSecond())
    }

    private const val MINUTES_PER_HOUR = 60

    /** How many days ahead the next change is looked for: a week and a day covers every weekly schedule. */
    private const val SEARCH_DAYS = 9
}
