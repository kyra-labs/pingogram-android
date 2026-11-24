package dev.kyralabs.pingogram.core.notifications

import dev.kyralabs.pingogram.core.domain.model.BirthdayReminder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** When a birthday reminder rings next: in the morning of the day, this year or the next. */
class BirthdayRingsTest {
    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(text: String): Instant = Instant.parse(text)

    @Test
    fun `a birthday ahead this year rings in its morning, local time`() {
        val ring = nextBirthdayRing(BirthdayReminder(7, 25, 11), at("2026-11-24T12:00:00Z"), zone)

        assertEquals(at("2026-11-25T08:00:00Z"), ring)
    }

    @Test
    fun `once the morning of the day has passed, it rings next year`() {
        val ring = nextBirthdayRing(BirthdayReminder(7, 24, 11), at("2026-11-24T08:00:01Z"), zone)

        assertEquals(at("2027-11-24T08:00:00Z"), ring)
    }

    @Test
    fun `before the morning of the day, it rings that morning`() {
        val ring = nextBirthdayRing(BirthdayReminder(7, 24, 11), at("2026-11-24T05:00:00Z"), zone)

        assertEquals(at("2026-11-24T08:00:00Z"), ring)
    }

    @Test
    fun `29 February rings on the 28th outside leap years`() {
        val reminder = BirthdayReminder(7, 29, 2)

        assertEquals(at("2027-02-28T08:00:00Z"), nextBirthdayRing(reminder, at("2026-11-24T12:00:00Z"), zone))
        assertEquals(at("2028-02-29T08:00:00Z"), nextBirthdayRing(reminder, at("2027-11-24T12:00:00Z"), zone))
        assertTrue(isBirthdayOf(reminder, LocalDate.of(2027, 2, 28)))
        assertFalse(isBirthdayOf(reminder, LocalDate.of(2028, 2, 28)))
    }
}
