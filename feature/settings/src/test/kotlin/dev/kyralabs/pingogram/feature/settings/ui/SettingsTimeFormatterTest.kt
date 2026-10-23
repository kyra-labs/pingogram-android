package dev.kyralabs.pingogram.feature.settings.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Instant

class SettingsTimeFormatterTest {
    private fun lastActive(
        time: String,
        uses24HourClock: Boolean = false,
    ) = plainSpaces(
        testTimeFormats(uses24HourClock = uses24HourClock)
            .formatter()
            .lastActive(Instant.parse(time).epochSecond, TEST_NOW),
    )

    @Test
    fun `today shows the time on the device's clock`() {
        assertEquals("8:05 AM", lastActive("2026-10-18T08:05:00Z"))
        assertEquals("08:05", lastActive("2026-10-18T08:05:00Z", uses24HourClock = true))
    }

    @Test
    fun `a time ahead of the clock counts as today`() {
        assertEquals("10:15 AM", lastActive("2026-10-18T10:15:00Z"))
    }

    @Test
    fun `the six days before today show the weekday`() {
        assertEquals("Sat", lastActive("2026-10-17T23:59:59Z"))
        assertEquals("Mon", lastActive("2026-10-12T00:00:00Z"))
    }

    @Test
    fun `earlier this year shows the day and month, before that the date`() {
        assertEquals("Oct 11", lastActive("2026-10-11T23:59:59Z"))
        assertEquals("12/31/2025", lastActive("2025-12-31T23:59:59Z"))
    }

    @Test
    fun `a moment in full shows the date and the time`() {
        val formatter = testTimeFormats().formatter()

        assertEquals(
            "Mar 17, 2026, 9:41 AM",
            plainSpaces(formatter.dateTime(Instant.parse("2026-03-17T09:41:00Z").epochSecond)),
        )
    }

    @Test
    fun `the formatter is kept while the settings stay the same`() {
        val formats = testTimeFormats()

        assertSame(formats.formatter(), formats.formatter())
    }

    @Test
    fun `an end today shows the time, a later one the date and the time`() {
        val formatter = testTimeFormats().formatter()

        assertEquals(
            listOf("6:30 PM", "Oct 19, 2026, 8:00 AM"),
            listOf("2026-10-18T18:30:00Z", "2026-10-19T08:00:00Z").map {
                plainSpaces(formatter.until(Instant.parse(it).epochSecond, TEST_NOW))
            },
        )
    }
}
