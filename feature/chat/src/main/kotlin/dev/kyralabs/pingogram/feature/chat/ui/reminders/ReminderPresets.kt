package dev.kyralabs.pingogram.feature.chat.ui.reminders

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * When a reminder's quick choices ring, as of [now] in [zone] (the owner's board 29b): in an hour (to the minute),
 * this evening at 20:00 (null once it has passed), tomorrow at 9:00, and next Monday at 9:00 (owner decision
 * 2026-11-15: morning and evening are not configurable for now). Unix seconds; null for "Pick a date and time".
 */
internal fun presetAt(
    option: ReminderOption,
    now: Instant,
    zone: ZoneId,
): Long? {
    val local = now.atZone(zone)
    val today = local.toLocalDate()
    return when (option) {
        ReminderOption.InOneHour -> {
            now.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES).epochSecond
        }

        ReminderOption.ThisEvening -> {
            today
                .atTime(EVENING)
                .atZone(zone)
                .toEpochSecond()
                .takeIf { local.toLocalTime() < EVENING }
        }

        ReminderOption.TomorrowMorning -> {
            today
                .plusDays(1)
                .atTime(MORNING)
                .atZone(zone)
                .toEpochSecond()
        }

        ReminderOption.NextWeek -> {
            today
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .atTime(MORNING)
                .atZone(zone)
                .toEpochSecond()
        }

        ReminderOption.Custom -> {
            null
        }
    }
}

private val MORNING: LocalTime = LocalTime.of(9, 0)
private val EVENING: LocalTime = LocalTime.of(20, 0)
