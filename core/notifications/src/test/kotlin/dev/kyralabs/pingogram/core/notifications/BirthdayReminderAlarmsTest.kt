package dev.kyralabs.pingogram.core.notifications

import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.BirthdayReminder
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
import java.time.ZoneId
import java.time.ZoneOffset

/** Which birthday reminders have their next ring scheduled, account by account, as they change. */
@OptIn(ExperimentalCoroutinesApi::class)
class BirthdayReminderAlarmsTest {
    private class RecordingWork : BirthdayReminderWork {
        val synced = mutableListOf<Pair<Int, Map<Long, Instant>>>()

        override suspend fun sync(
            slot: Int,
            rings: Map<BirthdayReminder, Instant>,
        ) {
            synced += slot to rings.mapKeys { it.key.userId }
        }
    }

    private val clock =
        object : WallClock {
            override val minutes = MutableStateFlow(NOW)

            override fun now(): Instant = NOW

            override fun zone(): ZoneId = ZoneOffset.UTC
        }

    private val first = MutableStateFlow(listOf(BirthdayReminder(7, 25, 11)))
    private val accounts = MutableStateFlow(listOf(BirthdayReminderAccount(0, first)))
    private val work = RecordingWork()

    private fun TestScope.started() {
        BirthdayReminderAlarms(accounts, work, clock, backgroundScope).start()
        advanceTimeBy(BirthdayReminderAlarms.START_DELAY.inWholeMilliseconds + 1)
        runCurrent()
    }

    @Test
    fun `nothing is scheduled before the start settled`() =
        runTest {
            BirthdayReminderAlarms(accounts, work, clock, backgroundScope).start()
            runCurrent()

            assertTrue(work.synced.isEmpty())
        }

    @Test
    fun `each reminder is scheduled for its next morning, and a change schedules them again`() =
        runTest {
            started()
            first.value = listOf(BirthdayReminder(7, 25, 11), BirthdayReminder(8, 1, 1))
            runCurrent()

            assertEquals(
                listOf(
                    0 to mapOf(7L to Instant.parse("2026-11-25T09:00:00Z")),
                    0 to
                        mapOf(
                            7L to Instant.parse("2026-11-25T09:00:00Z"),
                            8L to Instant.parse("2027-01-01T09:00:00Z"),
                        ),
                ),
                work.synced,
            )
        }

    @Test
    fun `an account without reminders schedules nothing, and one that leaves has its work cancelled`() =
        runTest {
            val second = MutableStateFlow(emptyList<BirthdayReminder>())
            accounts.value = listOf(BirthdayReminderAccount(0, first), BirthdayReminderAccount(1, second))
            started()
            assertEquals(listOf(0), work.synced.map { it.first })

            accounts.value = listOf(BirthdayReminderAccount(1, second))
            runCurrent()

            assertEquals(0 to emptyMap<Long, Instant>(), work.synced.last())
        }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-24T12:00:00Z")
    }
}
