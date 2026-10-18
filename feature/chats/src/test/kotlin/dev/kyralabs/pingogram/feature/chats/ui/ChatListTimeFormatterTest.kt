package dev.kyralabs.pingogram.feature.chats.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class ChatListTimeFormatterTest {
    private val now = Instant.parse("2026-10-18T09:41:30Z")

    private fun format(
        time: String,
        zone: ZoneId = ZoneOffset.UTC,
        is24HourClock: Boolean = false,
        patterns: TimePatterns = FakeTimePatterns(),
    ) = ChatListTimeFormatter(zone, Locale.US, is24HourClock, patterns)
        .format(Instant.parse(time).epochSecond, now)
        .replace(' ', ' ')

    @Test
    fun `today shows the time on the 12-hour clock`() {
        assertEquals("12:00 AM", format("2026-10-18T00:00:00Z"))
    }

    @Test
    fun `today shows the time on the 24-hour clock`() {
        assertEquals("00:00", format("2026-10-18T00:00:00Z", is24HourClock = true))
        assertEquals("21:07", format("2026-10-18T21:07:00Z", is24HourClock = true))
    }

    @Test
    fun `a time ahead of the clock counts as today`() {
        assertEquals("10:15 AM", format("2026-10-18T10:15:00Z"))
    }

    @Test
    fun `the six days before today show the weekday`() {
        assertEquals("Sat", format("2026-10-17T23:59:59Z"))
        assertEquals("Mon", format("2026-10-12T00:00:00Z"))
    }

    @Test
    fun `earlier this year shows the day and month`() {
        assertEquals("Oct 11", format("2026-10-11T23:59:59Z"))
        assertEquals("Jan 1", format("2026-01-01T00:00:00Z"))
    }

    @Test
    fun `earlier years show the date`() {
        assertEquals("12/31/2025", format("2025-12-31T23:59:59Z"))
    }

    @Test
    fun `days are counted in the user's time zone`() {
        // Yesterday in UTC, but already today in Tokyo (UTC+9).
        assertEquals("8:30 AM", format("2026-10-17T23:30:00Z", zone = ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun `a platform pattern java_time cannot read falls back to its localized style`() {
        // ICU's "b" (am, pm, noon, midnight), which java.time does not know.
        val dayPeriods =
            object : TimePatterns {
                override fun is24HourClock() = false

                override fun bestPattern(
                    locale: Locale,
                    skeleton: String,
                ) = "h:mm b"
            }

        assertEquals("9:05 AM", format("2026-10-18T09:05:00Z", patterns = dayPeriods))
    }

    @Test
    fun `a formatter is for its zone, language and clock setting`() {
        val formatter = ChatListTimeFormatter(ZoneOffset.UTC, Locale.US, is24HourClock = false, FakeTimePatterns())

        assertTrue(formatter.isFor(ZoneOffset.UTC, Locale.US, is24HourClock = false))
        assertFalse(formatter.isFor(ZoneOffset.UTC, Locale.US, is24HourClock = true))
        assertFalse(formatter.isFor(ZoneOffset.UTC, Locale.UK, is24HourClock = false))
        assertFalse(formatter.isFor(ZoneId.of("Asia/Tokyo"), Locale.US, is24HourClock = false))
    }
}
