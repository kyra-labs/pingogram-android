package dev.kyralabs.pingogram.core.ui.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnnouncedCountdownTest {
    @Test
    fun `under a minute, announcements round up to 15 second steps`() {
        assertEquals(
            listOf(15L, 15L, 30L, 45L, 60L, 60L),
            listOf(1L, 15L, 16L, 42L, 46L, 59L).map(::announcedCountdownSeconds),
        )
    }

    @Test
    fun `zero and longer times are left to durationText`() {
        assertEquals(listOf(0L, 60L, 299L), listOf(0L, 60L, 299L).map(::announcedCountdownSeconds))
    }
}
