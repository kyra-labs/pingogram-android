package dev.kyralabs.pingogram.core.common.time

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SystemWallClockTest {
    /** A system clock that starts at [START] and runs with the test's virtual time. */
    private fun TestScope.wallClock(): SystemWallClock {
        val clock =
            object : Clock() {
                override fun instant(): Instant = START.plusMillis(testScheduler.currentTime)

                override fun getZone(): ZoneId = ZoneOffset.UTC

                override fun withZone(zone: ZoneId?): Clock = this
            }
        return SystemWallClock(backgroundScope, clock) { ZoneOffset.UTC }
    }

    private fun TestScope.collect(
        wallClock: WallClock,
        into: MutableList<Instant>,
    ): Job = backgroundScope.launch { wallClock.minutes.collect { into += it } }.also { runCurrent() }

    @Test
    fun `now and zone come from the system`() =
        runTest {
            val wallClock = wallClock()

            assertEquals(START, wallClock.now())
            assertEquals(ZoneOffset.UTC, wallClock.zone())
        }

    @Test
    fun `a collector gets the current minute at once`() =
        runTest {
            val minutes = mutableListOf<Instant>()

            collect(wallClock(), minutes)

            assertEquals(listOf(minute("09:41")), minutes)
        }

    @Test
    fun `ticks when the wall clock reaches each next minute`() =
        runTest {
            val minutes = mutableListOf<Instant>()
            collect(wallClock(), minutes)

            // START is 30.5 seconds into its minute.
            advanceTimeBy(29_499)
            runCurrent()
            assertEquals(listOf(minute("09:41")), minutes)

            advanceTimeBy(1)
            runCurrent()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(listOf(minute("09:41"), minute("09:42"), minute("09:43")), minutes)
        }

    @Test
    fun `collectors share one timer`() =
        runTest {
            val wallClock = wallClock()
            val first = mutableListOf<Instant>()
            val second = mutableListOf<Instant>()
            collect(wallClock, first)
            advanceTimeBy(29_500)
            runCurrent()

            collect(wallClock, second)

            assertEquals(listOf(minute("09:41"), minute("09:42")), first)
            assertEquals(listOf(minute("09:42")), second)
        }

    @Test
    fun `a collector after a pause gets the current minute, not the one before the pause`() =
        runTest {
            val wallClock = wallClock()
            collect(wallClock, mutableListOf()).cancel()
            advanceTimeBy(10 * 60_000)

            val minutes = mutableListOf<Instant>()
            collect(wallClock, minutes)

            assertEquals(listOf(minute("09:51")), minutes)
        }

    private fun minute(time: String): Instant = Instant.parse("2026-10-18T$time:00Z")

    private companion object {
        val START: Instant = Instant.parse("2026-10-18T09:41:30.500Z")
    }
}
