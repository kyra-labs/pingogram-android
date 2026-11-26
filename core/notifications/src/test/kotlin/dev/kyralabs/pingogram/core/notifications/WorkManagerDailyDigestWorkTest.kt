package dev.kyralabs.pingogram.core.notifications

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Duration
import java.time.Instant

/** The Daily digest's work in WorkManager: the next time replaces another, not one that is overdue. */
@RunWith(RobolectricTestRunner::class)
class WorkManagerDailyDigestWorkTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private var now = Instant.parse("2026-11-26T06:00:00Z")
    private val work = WorkManagerDailyDigestWork(context) { now }

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    private fun stateOf(at: Instant): WorkInfo.State? =
        WorkManager
            .getInstance(context)
            .getWorkInfosForUniqueWork(WorkManagerDailyDigestWork.workName(at))
            .get()
            .singleOrNull()
            ?.state

    @Test
    fun `work held up past its time is kept when the next time is scheduled`() =
        runTest {
            val today = Instant.parse("2026-11-26T08:00:00Z")
            val tomorrow = today + Duration.ofDays(1)
            work.sync(today)

            // Doze held the work up; the app starts after its time and schedules the next one.
            now = Instant.parse("2026-11-26T09:30:00Z")
            work.sync(tomorrow)

            assertEquals(WorkInfo.State.ENQUEUED, stateOf(today))
            assertEquals(WorkInfo.State.ENQUEUED, stateOf(tomorrow))
        }

    @Test
    fun `a time not due yet is replaced, and turning the digest off cancels everything`() =
        runTest {
            val eight = Instant.parse("2026-11-26T08:00:00Z")
            val nine = Instant.parse("2026-11-26T09:00:00Z")
            work.sync(eight)

            work.sync(nine)
            assertEquals(WorkInfo.State.CANCELLED, stateOf(eight))

            now = Instant.parse("2026-11-26T10:00:00Z")
            work.sync(null)
            assertEquals(WorkInfo.State.CANCELLED, stateOf(nine))
        }
}
