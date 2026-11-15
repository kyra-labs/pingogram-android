package dev.kyralabs.pingogram.feature.chat.ui.reminders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** The reminder's quick choices (board 29b): morning 9:00 and evening 20:00, owner decision 2026-11-15. */
class ReminderPresetsTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(text: String): Instant = ZonedDateTime.parse("$text+05:30[Asia/Kolkata]").toInstant()

    private fun seconds(text: String): Long = at(text).epochSecond

    // A Thursday morning.
    private val now = at("2026-11-12T10:41:30")

    @Test
    fun `in an hour is to the minute`() {
        assertEquals(seconds("2026-11-12T11:41:00"), presetAt(ReminderOption.InOneHour, now, zone))
    }

    @Test
    fun `this evening is 8 PM today, and none once it has passed`() {
        assertEquals(seconds("2026-11-12T20:00:00"), presetAt(ReminderOption.ThisEvening, now, zone))
        assertNull(presetAt(ReminderOption.ThisEvening, at("2026-11-12T20:00:00"), zone))
    }

    @Test
    fun `tomorrow morning is 9 AM tomorrow`() {
        assertEquals(seconds("2026-11-13T09:00:00"), presetAt(ReminderOption.TomorrowMorning, now, zone))
    }

    @Test
    fun `next week is next Monday at 9 AM, also on a Monday`() {
        assertEquals(seconds("2026-11-16T09:00:00"), presetAt(ReminderOption.NextWeek, now, zone))
        assertEquals(seconds("2026-11-23T09:00:00"), presetAt(ReminderOption.NextWeek, at("2026-11-16T08:00:00"), zone))
    }

    @Test
    fun `a picked date and time has no preset`() {
        assertNull(presetAt(ReminderOption.Custom, now, zone))
    }
}
