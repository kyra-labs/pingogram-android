package dev.kyralabs.pingogram.feature.chat.ui.reminders

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** The quick choices of the reminder sheet (the owner's board 29), and "Pick a date and time". */
enum class ReminderChoice {
    InOneHour,

    /** 20:00 today; offered only before then. */
    ThisEvening,

    /** 9:00 tomorrow. */
    TomorrowMorning,

    /** 9:00 next Monday. */
    NextWeek,

    /** The moment picked with the date and time pickers. */
    Custom,
}

/**
 * When each quick choice rings, as of [now] in [zone]: owner decision 2026-11-15, morning is 9:00 and evening 20:00,
 * not configurable for now. An hour from now keeps the minute, without seconds.
 */
internal object ReminderTimes {
    val MORNING: LocalTime = LocalTime.of(9, 0)
    val EVENING: LocalTime = LocalTime.of(20, 0)

    /** The moment [choice] stands for; null where it is not offered now ([ReminderChoice.ThisEvening] after 20:00). */
    fun at(
        choice: ReminderChoice,
        now: Instant,
        zone: ZoneId,
    ): Instant? {
        val today = now.atZone(zone)
        return when (choice) {
            ReminderChoice.InOneHour -> {
                now.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES)
            }

            ReminderChoice.ThisEvening -> {
                today
                    .with(EVENING)
                    .truncatedTo(ChronoUnit.MINUTES)
                    .toInstant()
                    .takeIf { it.isAfter(now) }
            }

            ReminderChoice.TomorrowMorning -> {
                today.plusDays(1).with(MORNING).toInstant()
            }

            ReminderChoice.NextWeek -> {
                today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(MORNING).toInstant()
            }

            ReminderChoice.Custom -> {
                null
            }
        }
    }
}
