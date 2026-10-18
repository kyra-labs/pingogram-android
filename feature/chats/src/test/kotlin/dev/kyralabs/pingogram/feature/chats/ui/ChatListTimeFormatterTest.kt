package dev.kyralabs.pingogram.feature.chats.ui

import org.junit.jupiter.api.Assertions.assertEquals
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
        locale: Locale = Locale.US,
    ) = ChatListTimeFormatter(zone, locale).format(Instant.parse(time).epochSecond, now).replace(' ', ' ')

    @Test
    fun `today shows the time`() {
        assertEquals("12:00 AM", format("2026-10-18T00:00:00Z"))
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
        assertEquals("12/31/25", format("2025-12-31T23:59:59Z"))
    }

    @Test
    fun `days are counted in the user's time zone`() {
        // Yesterday in UTC, but already today in Tokyo (UTC+9).
        assertEquals("8:30 AM", format("2026-10-17T23:30:00Z", zone = ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun `the day and month follow the language`() {
        assertEquals("11 Oct", format("2026-10-11T10:00:00Z", locale = Locale.UK))
        assertEquals("11.10", format("2026-10-11T10:00:00Z", locale = Locale.GERMANY))
    }

    @Test
    fun `withoutYear removes the year with the separators that belong to it`() {
        assertEquals("MMM d", withoutYear("MMM d, y"))
        assertEquals("d MMM", withoutYear("d MMM y 'г'."))
        assertEquals("MM/dd", withoutYear("y/MM/dd"))
        assertEquals("dd.MM", withoutYear("dd.MM.y"))
        assertEquals("M月d日", withoutYear("y年M月d日"))
        assertEquals("d 'de' MMM", withoutYear("d 'de' MMM 'de' y"))
        assertEquals("d MMM, EEE", withoutYear("d MMM y, EEE"))
    }

    @Test
    fun `withoutYear keeps a pattern without a year`() {
        assertEquals("d MMMM", withoutYear("d MMMM"))
    }
}
