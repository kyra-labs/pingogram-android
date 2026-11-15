package dev.kyralabs.pingogram.core.notifications

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Reminder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Which reminders have work waiting, scheduled again when their time moves and cancelled when done or deleted. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReminderAlarmsTest {
    private class RecordingWork : ReminderWork {
        val waiting = mutableMapOf<Pair<Int, String>, Long>()
        val calls = mutableListOf<String>()

        override suspend fun scheduled(slot: Int): Map<String, Long> =
            waiting.filterKeys { it.first == slot }.mapKeys { it.key.second }

        override fun schedule(
            slot: Int,
            reminder: Reminder,
            delayMillis: Long,
        ) {
            waiting[slot to reminder.key] = reminder.at
            calls += "schedule $slot ${reminder.key} in $delayMillis"
        }

        override fun cancel(
            slot: Int,
            key: String,
        ) {
            waiting.remove(slot to key)
            calls += "cancel $slot $key"
        }

        override suspend fun cancelAll(slot: Int) {
            waiting.keys.removeAll { it.first == slot }
            calls += "cancel all $slot"
        }
    }

    private object Clock : WallClock {
        override fun now(): Instant = Instant.ofEpochSecond(NOW)

        override fun zone(): ZoneId = ZoneOffset.UTC

        override val minutes: SharedFlow<Instant> = MutableSharedFlow()
    }

    private val first = MutableStateFlow<List<Reminder>>(emptyList())
    private val second = MutableStateFlow<List<Reminder>>(emptyList())
    private val accounts = MutableStateFlow(listOf(ReminderAccount(0, first)))
    private val work = RecordingWork()

    private fun TestScope.started() {
        ReminderAlarms(accounts, work, Clock, backgroundScope).start()
        advanceTimeBy(ReminderAlarms.START_DELAY.inWholeMilliseconds + 1)
        runCurrent()
    }

    private fun reminder(
        messageId: Long,
        at: Long,
        done: Boolean = false,
    ) = Reminder(chatId = 7, messageId = messageId, at = at, done = done)

    @Test
    fun `nothing is scheduled before the start settled`() =
        runTest {
            first.value = listOf(reminder(1, NOW + 60))
            ReminderAlarms(accounts, work, Clock, backgroundScope).start()

            advanceTimeBy(ReminderAlarms.START_DELAY.inWholeMilliseconds - 1)
            assertTrue(work.calls.isEmpty())
            advanceTimeBy(2)
            assertEquals(listOf("schedule 0 7_1 in 60000"), work.calls)
        }

    @Test
    fun `a reminder due is scheduled at its time, and a past one at once`() =
        runTest {
            first.value = listOf(reminder(1, NOW + 3_600), reminder(2, NOW - 10), reminder(3, NOW + 5, done = true))
            started()

            assertEquals(listOf("schedule 0 7_1 in 3600000", "schedule 0 7_2 in 0"), work.calls)
        }

    @Test
    fun `work already waiting for the same time is left as it is`() =
        runTest {
            work.waiting[0 to "7_1"] = NOW + 60
            first.value = listOf(reminder(1, NOW + 60))
            started()

            assertTrue(work.calls.isEmpty())
        }

    @Test
    fun `a moved reminder is scheduled again, a done or deleted one cancelled`() =
        runTest {
            first.value = listOf(reminder(1, NOW + 60), reminder(2, NOW + 120))
            started()
            work.calls.clear()

            first.value = listOf(reminder(1, NOW + 600), reminder(2, NOW + 120, done = true))
            runCurrent()
            first.value = emptyList()
            runCurrent()

            assertEquals(listOf("cancel 0 7_2", "schedule 0 7_1 in 600000", "cancel 0 7_1"), work.calls)
        }

    @Test
    fun `each account is followed, and one that leaves the device takes its work with it`() =
        runTest {
            started()
            second.value = listOf(reminder(1, NOW + 60))
            accounts.value = listOf(ReminderAccount(0, first), ReminderAccount(2, second))
            runCurrent()
            accounts.value = listOf(ReminderAccount(0, first))
            runCurrent()

            assertEquals(listOf("schedule 2 7_1 in 60000", "cancel all 2"), work.calls)
        }

    @Test
    fun `WorkManager's work is tagged with its time and replaced when the time moves`() {
        val context: Application = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        val manager = WorkManager.getInstance(context)
        val work = WorkManagerReminderWork(context)

        work.schedule(1, reminder(1, at = 500), delayMillis = 60_000)
        work.schedule(1, reminder(1, at = 900), delayMillis = 60_000)
        work.schedule(1, reminder(2, at = 700), delayMillis = 60_000)

        assertEquals(mapOf("7_1" to 900L, "7_2" to 700L), runBlocking { work.scheduled(1) })
        work.cancel(1, "7_2")
        assertEquals(
            WorkInfo.State.CANCELLED,
            manager
                .getWorkInfosForUniqueWork(WorkManagerReminderWork.workName(1, "7_2"))
                .get()
                .single()
                .state,
        )
        runBlocking { work.cancelAll(1) }
        assertTrue(runBlocking { work.scheduled(1) }.isEmpty())
    }

    private companion object {
        const val NOW = 1_800_000_000L
    }
}
