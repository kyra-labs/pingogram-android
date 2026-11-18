package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.FoundEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * [foundEventOf]: the dates and times Android's text classifier finds, read as the language writes them; what they do
 * not say comes from when the message was written; words that name no moment start nowhere.
 */
class FoundEventTimeTest {
    private val zone = ZoneId.of("Europe/London")
    private val writtenAt = ZonedDateTime.of(2026, 11, 17, 10, 0, 0, 0, zone)

    private fun at(dateTime: LocalDateTime) = dateTime.atZone(zone).toInstant().toEpochMilli()

    private fun day(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `a date is a whole day`() {
        assertEquals(FoundEvent(day(LocalDate.of(2026, 11, 20)), isAllDay = true), event("20/11/2026", Locale.UK))
        assertEquals(FoundEvent(day(LocalDate.of(2026, 11, 20)), isAllDay = true), event("2026-11-20", Locale.UK))
    }

    @Test
    fun `a day without its year takes the message's`() {
        assertEquals(FoundEvent(day(LocalDate.of(2026, 11, 20)), isAllDay = true), event("20 November", Locale.UK))
        assertEquals(FoundEvent(day(LocalDate.of(2026, 12, 3)), isAllDay = true), event("December 3", Locale.US))
    }

    @Test
    fun `a time alone is on the day the message was written`() {
        val expected = FoundEvent(at(LocalDateTime.of(2026, 11, 17, 19, 30)), isAllDay = false)

        assertEquals(expected, event("19:30", Locale.UK))
        assertEquals(expected, event("7:30 pm", Locale.US))
        assertEquals(expected, event("7:30 PM", Locale.US))
    }

    @Test
    fun `a date with a time starts then`() {
        val expected = FoundEvent(at(LocalDateTime.of(2026, 11, 20, 19, 0)), isAllDay = false)

        assertEquals(expected, event("2026-11-20T19:00", Locale.UK))
    }

    @Test
    fun `words that name no moment start nowhere`() {
        assertEquals(FoundEvent(startMillis = null, isAllDay = false), event("tomorrow evening", Locale.UK))
    }

    private fun event(
        text: String,
        locale: Locale,
    ) = foundEventOf(text, locale, writtenAt)
}
