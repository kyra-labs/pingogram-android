package dev.kyralabs.pingogram.core.notifications

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

/** When each account's weekly clean-up is scheduled and cancelled, and how. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StorageOptimizationTest {
    private class RecordingWork : StorageOptimizationWork {
        val calls = mutableListOf<String>()

        override fun schedule(slot: Int) {
            calls += "schedule $slot"
        }

        override fun cancel(slot: Int) {
            calls += "cancel $slot"
        }
    }

    private val first = MutableStateFlow<AuthStep>(AuthStep.Initializing)
    private val second = MutableStateFlow<AuthStep>(AuthStep.Ready)
    private val accounts = MutableStateFlow(listOf(StorageAccount(0, first)))
    private val work = RecordingWork()

    private fun TestScope.started() {
        StorageOptimization(accounts, work, backgroundScope).start()
        runCurrent()
    }

    @Test
    fun `nothing is scheduled before the start settled`() =
        runTest {
            first.value = AuthStep.Ready
            started()

            advanceTimeBy(StorageOptimization.START_DELAY.inWholeMilliseconds - 1)
            assertTrue(work.calls.isEmpty())
            advanceTimeBy(2)
            assertEquals(listOf("schedule 0"), work.calls)
        }

    @Test
    fun `an account is scheduled once logged in and cancelled as it logs out`() =
        runTest {
            started()
            advanceTimeBy(StorageOptimization.START_DELAY.inWholeMilliseconds + 1)
            assertTrue(work.calls.isEmpty())

            first.value = AuthStep.Ready
            runCurrent()
            first.value = AuthStep.LoggingOut
            runCurrent()

            assertEquals(listOf("schedule 0", "cancel 0"), work.calls)
        }

    @Test
    fun `each account is scheduled, and one that leaves the device is cancelled`() =
        runTest {
            first.value = AuthStep.Ready
            started()
            advanceTimeBy(StorageOptimization.START_DELAY.inWholeMilliseconds + 1)

            accounts.value = listOf(StorageAccount(0, first), StorageAccount(2, second))
            runCurrent()
            accounts.value = listOf(StorageAccount(0, first))
            runCurrent()
            second.value = AuthStep.LoggingOut
            runCurrent()

            assertEquals(listOf("schedule 0", "schedule 2", "cancel 2"), work.calls)
        }

    @Test
    fun `the work runs weekly while charging and idle, and a second schedule keeps the first`() {
        val context: Application = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        val manager = WorkManager.getInstance(context)
        val work = WorkManagerStorageOptimizationWork(context)
        val name = WorkManagerStorageOptimizationWork.workName(1)

        work.schedule(1)
        val scheduled = manager.getWorkInfosForUniqueWork(name).get().single()
        work.schedule(1)

        val info = manager.getWorkInfosForUniqueWork(name).get().single()
        assertEquals(scheduled.id, info.id)
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(7 * 24 * 60 * 60 * 1000L, info.periodicityInfo?.repeatIntervalMillis)
        assertTrue(info.constraints.requiresCharging())
        assertTrue(info.constraints.requiresDeviceIdle())
        assertTrue(!info.constraints.requiresStorageNotLow())

        work.cancel(1)
        assertEquals(
            WorkInfo.State.CANCELLED,
            manager
                .getWorkInfosForUniqueWork(name)
                .get()
                .single()
                .state,
        )
    }
}
