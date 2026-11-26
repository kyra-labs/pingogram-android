package dev.kyralabs.pingogram.core.notifications

import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.repository.DailyDigestSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** When the Daily digest is scheduled (`REQUIREMENTS.md` FR-X23): its next time while on, nothing once off. */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyDigestScheduleTest {
    private class RecordingWork : DailyDigestWork {
        val synced = mutableListOf<Instant?>()

        override suspend fun sync(at: Instant?) {
            synced += at
        }

        override fun enqueue(at: Instant) = error("the schedule syncs")
    }

    private val clock =
        object : WallClock {
            override val minutes = MutableStateFlow(NOW)

            override fun now(): Instant = NOW

            override fun zone(): ZoneId = ZoneOffset.UTC
        }

    private val settings = MutableStateFlow(DailyDigestSettings())
    private val work = RecordingWork()

    private fun TestScope.started() {
        DailyDigestSchedule(settings, work, clock, backgroundScope).start()
        advanceTimeBy(DailyDigestSchedule.START_DELAY.inWholeMilliseconds + 1)
        runCurrent()
    }

    @Test
    fun `nothing is scheduled while the digest was never on`() =
        runTest {
            started()

            assertTrue(work.synced.isEmpty())
        }

    @Test
    fun `turned on it is scheduled, a new time moves it, and turned off it is cancelled`() =
        runTest {
            started()
            settings.value = DailyDigestSettings(enabled = true)
            runCurrent()
            settings.value = DailyDigestSettings(enabled = true, time = LocalTime.of(21, 0))
            runCurrent()
            settings.value = DailyDigestSettings(enabled = false)
            runCurrent()

            assertEquals(
                listOf(Instant.parse("2026-11-27T08:30:00Z"), Instant.parse("2026-11-26T21:00:00Z"), null),
                work.synced,
            )
        }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-26T09:00:00Z")
    }
}
