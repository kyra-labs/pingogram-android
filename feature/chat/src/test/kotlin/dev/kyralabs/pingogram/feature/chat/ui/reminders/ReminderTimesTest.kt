package dev.kyralabs.pingogram.feature.chat.ui.reminders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** When the quick choices ring (owner decision 2026-11-15: morning 9:00, evening 20:00). */
class ReminderTimesTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(
        choice: ReminderChoice,
        now: String,
    ) = ReminderTimes.at(choice, Instant.parse(now), zone)

    @Test
    fun `an hour from now keeps the minute without its seconds`() {
        assertEquals(Instant.parse("2026-11-21T17:30:00Z"), at(ReminderChoice.InOneHour, "2026-11-21T16:30:45Z"))
    }

    @Test
    fun `this evening is 20 00 today, offered only before then`() {
        // 4:30 PM in Kolkata.
        assertEquals(Instant.parse("2026-11-21T14:30:00Z"), at(ReminderChoice.ThisEvening, "2026-11-21T11:00:00Z"))
        // 10:30 PM in Kolkata: gone by.
        assertNull(at(ReminderChoice.ThisEvening, "2026-11-21T17:00:00Z"))
    }

    @Test
    fun `tomorrow morning is 9 00 the next day, and next week 9 00 next Monday`() {
        // Saturday, 10:30 PM in Kolkata (board 29b).
        val now = "2026-11-21T17:00:00Z"
        assertEquals(Instant.parse("2026-11-22T03:30:00Z"), at(ReminderChoice.TomorrowMorning, now))
        assertEquals(Instant.parse("2026-11-23T03:30:00Z"), at(ReminderChoice.NextWeek, now))
    }

    @Test
    fun `next week starts on the first day of the week given`() {
        // Saturday, 10:30 PM in Kolkata: a week that starts on Sunday starts tomorrow.
        val now = Instant.parse("2026-11-21T17:00:00Z")
        assertEquals(
            Instant.parse("2026-11-22T03:30:00Z"),
            ReminderTimes.at(ReminderChoice.NextWeek, now, zone, DayOfWeek.SUNDAY),
        )
        // Monday, 10:30 AM: a week that starts on Monday starts in seven days.
        assertEquals(
            Instant.parse("2026-11-30T03:30:00Z"),
            ReminderTimes.at(ReminderChoice.NextWeek, Instant.parse("2026-11-23T05:00:00Z"), zone, DayOfWeek.MONDAY),
        )
    }

    @Test
    fun `a picked moment has no quick time`() {
        assertNull(at(ReminderChoice.Custom, "2026-11-21T17:00:00Z"))
    }
}
