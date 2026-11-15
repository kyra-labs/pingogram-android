package dev.kyralabs.pingogram.core.notifications

import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
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

/** Which reminders have their ring scheduled, account by account, as they change. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderAlarmsTest {
    private class RecordingWork : ReminderWork {
        val synced = mutableListOf<Pair<Int, List<Long>>>()

        override suspend fun sync(
            slot: Int,
            upcoming: List<MessageReminder>,
        ) {
            synced += slot to upcoming.map { it.messageId }
        }
    }

    private val clock =
        object : WallClock {
            override val minutes = MutableStateFlow(NOW)

            override fun now(): Instant = NOW

            override fun zone(): ZoneId = ZoneOffset.UTC
        }

    private fun reminder(
        messageId: Long,
        inSeconds: Long,
        isDone: Boolean = false,
    ) = MessageReminder(chatId = 7, messageId = messageId, at = NOW.plusSeconds(inSeconds), isDone = isDone)

    private val first = MutableStateFlow(listOf(reminder(1, 60)))
    private val accounts = MutableStateFlow(listOf(ReminderAccount(0, first)))
    private val work = RecordingWork()

    private fun TestScope.started() {
        ReminderAlarms(accounts, work, clock, backgroundScope).start()
        advanceTimeBy(ReminderAlarms.START_DELAY.inWholeMilliseconds + 1)
        runCurrent()
    }

    @Test
    fun `nothing is scheduled before the start settled`() =
        runTest {
            ReminderAlarms(accounts, work, clock, backgroundScope).start()
            advanceTimeBy(ReminderAlarms.START_DELAY.inWholeMilliseconds - 1)
            assertTrue(work.synced.isEmpty())
            advanceTimeBy(2)
            assertEquals(listOf(0 to listOf(1L)), work.synced)
        }

    @Test
    fun `only reminders not done and still ahead are scheduled, again as they change`() =
        runTest {
            started()

            first.value = listOf(reminder(1, 60, isDone = true), reminder(2, -60), reminder(3, 3_600))
            runCurrent()

            assertEquals(listOf(0 to listOf(1L), 0 to listOf(3L)), work.synced)
        }

    @Test
    fun `each account is followed, and one that leaves the device has its rings cancelled`() =
        runTest {
            started()
            val second = MutableStateFlow(listOf(reminder(5, 60)))

            accounts.value = listOf(ReminderAccount(0, first), ReminderAccount(1, second))
            runCurrent()
            accounts.value = listOf(ReminderAccount(0, first))
            runCurrent()

            assertEquals(listOf(0 to listOf(1L), 1 to listOf(5L), 1 to emptyList<Long>()), work.synced)
        }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-15T12:00:00Z")
    }
}
