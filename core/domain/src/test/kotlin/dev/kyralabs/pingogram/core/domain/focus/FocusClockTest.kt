package dev.kyralabs.pingogram.core.domain.focus

import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.domain.model.FocusProfile
import dev.kyralabs.pingogram.core.domain.model.FocusSchedule
import dev.kyralabs.pingogram.core.domain.model.FocusUntil
import dev.kyralabs.pingogram.core.domain.model.ManualFocus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Which Focus profile is on at a moment (board 31): schedules, ranges across midnight, and Focus set by hand. */
class FocusClockTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private val work =
        profile(1, FocusSchedule(DayOfWeek.entries.take(5).toImmutableSet(), 9 * 60, 18 * 60))
    private val sleep = profile(2, FocusSchedule(DayOfWeek.entries.toImmutableSet(), 22 * 60 + 30, 7 * 60))
    private val weekend = profile(3, schedule = null)
    private val profiles = listOf(work, sleep, weekend)

    private fun profile(
        id: Long,
        schedule: FocusSchedule?,
    ) = FocusProfile(id, "P$id", FocusIcon.Work, persistentListOf(), persistentListOf(), schedule, false)

    /** 2026-11-16 is a Monday. */
    private fun at(
        day: Int,
        hour: Int,
        minute: Int = 0,
    ): Instant = LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant()

    private fun active(
        now: Instant,
        manual: ManualFocus? = null,
    ) = FocusClock.active(profiles, manual, now, zone)

    @Test
    fun `a schedule covers its days and hours, and ends at its end`() {
        val on = active(at(16, 10))!!
        assertEquals(work, on.profile)
        assertFalse(on.byHand)
        assertEquals(at(16, 18).epochSecond, on.endsAt)
        assertNull(active(at(16, 18)))
        assertNull(active(at(21, 10))) // Saturday.
    }

    @Test
    fun `a range across midnight is one range, into the next morning`() {
        assertEquals(sleep, active(at(16, 23))!!.profile)
        assertEquals(at(17, 7).epochSecond, active(at(16, 23))!!.endsAt)
        assertEquals(sleep, active(at(17, 6, 59))!!.profile)
        assertEquals(at(17, 7).epochSecond, active(at(17, 6))!!.endsAt)
        assertNull(active(at(17, 7)))
    }

    @Test
    fun `a range across midnight runs the morning after its last day too`() {
        val friday = sleep.copy(schedule = FocusSchedule(setOf(FRIDAY).toImmutableSet(), 22 * 60, 6 * 60))
        assertTrue(FocusClock.active(listOf(friday), null, at(21, 5), zone) != null) // Saturday morning.
        assertNull(FocusClock.active(listOf(friday), null, at(22, 5), zone)) // Sunday morning.
    }

    @Test
    fun `a profile turned on by hand wins until the schedule's next change`() {
        val manual = ManualFocus(3, at(16, 8).epochSecond, FocusUntil.ScheduleChange)

        val before = active(at(16, 8, 30), manual)!!
        assertEquals(weekend, before.profile)
        assertTrue(before.byHand)
        assertEquals(at(16, 9).epochSecond, before.endsAt)
        assertEquals(work, active(at(16, 9, 30), manual)!!.profile)
    }

    @Test
    fun `Focus turned off by hand stays off until the next change`() {
        val manual = ManualFocus(null, at(16, 10).epochSecond, FocusUntil.ScheduleChange)

        assertNull(active(at(16, 11), manual))
        assertNull(active(at(16, 18, 30), manual))
        assertEquals(sleep, active(at(16, 23), manual)!!.profile)
    }

    @Test
    fun `an hour lasts an hour, and until turned off lasts`() {
        val hour = ManualFocus(3, at(16, 10).epochSecond, FocusUntil.Time(at(16, 11).epochSecond))
        assertEquals(weekend, active(at(16, 10, 30), hour)!!.profile)
        assertEquals(work, active(at(16, 11, 30), hour)!!.profile)

        val forever = ManualFocus(3, at(16, 10).epochSecond, FocusUntil.TurnedOff)
        assertEquals(weekend, active(at(21, 10), forever)!!.profile)
        assertNull(active(at(21, 10), forever)!!.endsAt)
    }

    @Test
    fun `without schedules a profile set until the next change stays on`() {
        val manual = ManualFocus(3, at(16, 10).epochSecond, FocusUntil.ScheduleChange)

        assertEquals(weekend, FocusClock.active(listOf(weekend), manual, at(30, 10), zone)!!.profile)
    }

    @Test
    fun `the next change is the nearest start or end`() {
        assertEquals(at(16, 18).epochSecond, FocusClock.nextChange(profiles, at(16, 10).epochSecond, zone))
        assertEquals(at(16, 22, 30).epochSecond, FocusClock.nextChange(profiles, at(16, 18).epochSecond, zone))
        val saturday = listOf(work.copy(schedule = FocusSchedule(setOf(MONDAY, SATURDAY).toImmutableSet(), 60, 120)))
        assertEquals(at(21, 1).epochSecond, FocusClock.nextChange(saturday, at(17, 0).epochSecond, zone))
    }

    @Test
    fun `a schedule next starts today, later in the week, or never`() {
        assertEquals(at(16, 22, 30).epochSecond, FocusClock.nextStart(sleep.schedule!!, at(16, 10), zone))
        // Friday evening: work starts again on Monday.
        assertEquals(at(23, 9).epochSecond, FocusClock.nextStart(work.schedule!!, at(20, 19), zone))
        assertNull(FocusClock.nextStart(FocusSchedule(persistentSetOf(), 0, 60), at(16, 10), zone))
    }
}
