package dev.kyralabs.pingogram.core.notifications

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

/** When the next Daily digest comes ([nextDigestAt], `REQUIREMENTS.md` FR-X23). */
class NextDigestTest {
    @Test
    fun `the next time comes today until it has passed, then tomorrow`() {
        val zone = ZoneOffset.UTC
        val time = LocalTime.of(8, 30)

        assertEquals(
            Instant.parse("2026-11-26T08:30:00Z"),
            nextDigestAt(time, Instant.parse("2026-11-26T06:00:00Z"), zone),
        )
        assertEquals(
            Instant.parse("2026-11-27T08:30:00Z"),
            nextDigestAt(time, Instant.parse("2026-11-26T08:30:00Z"), zone),
        )
    }
}
