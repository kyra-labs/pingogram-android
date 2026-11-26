package dev.kyralabs.pingogram.core.notifications

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.kyralabs.pingogram.core.domain.model.BirthdayReminder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant

/** The birthday reminders' work in WorkManager: the next ring replaces another, not one that is overdue. */
@RunWith(RobolectricTestRunner::class)
class WorkManagerBirthdayReminderWorkTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private var now = Instant.parse("2026-11-26T06:00:00Z")
    private val work = WorkManagerBirthdayReminderWork(context) { now }
    private val ada = BirthdayReminder(userId = 7, day = 26, month = 11)
    private val grace = BirthdayReminder(userId = 8, day = 26, month = 11)
    private val today = Instant.parse("2026-11-26T09:00:00Z")
    private val nextYear = Instant.parse("2027-11-26T09:00:00Z")

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    private fun stateOf(
        reminder: BirthdayReminder,
        at: Instant,
    ): WorkInfo.State? =
        WorkManager
            .getInstance(context)
            .getWorkInfosForUniqueWork(WorkManagerBirthdayReminderWork.workName(SLOT, reminder, at))
            .get()
            .singleOrNull()
            ?.state

    @Test
    fun `a ring held up past its time is kept when next year's is scheduled`() =
        runTest {
            work.sync(SLOT, mapOf(ada to today))

            // Doze held the work up; the app starts after its time and schedules the next ring.
            now = Instant.parse("2026-11-26T11:00:00Z")
            work.sync(SLOT, mapOf(ada to nextYear))

            assertEquals(WorkInfo.State.ENQUEUED, stateOf(ada, today))
            assertEquals(WorkInfo.State.ENQUEUED, stateOf(ada, nextYear))
        }

    @Test
    fun `a ring not due yet, or of a reminder no longer set, is cancelled`() =
        runTest {
            work.sync(SLOT, mapOf(ada to today, grace to today))

            work.sync(SLOT, mapOf(ada to nextYear, grace to today))
            assertEquals(WorkInfo.State.CANCELLED, stateOf(ada, today))

            now = Instant.parse("2026-11-26T11:00:00Z")
            work.sync(SLOT, emptyMap())
            assertEquals(WorkInfo.State.CANCELLED, stateOf(grace, today))
        }

    private companion object {
        const val SLOT = 1
    }
}
