package dev.kyralabs.pingogram.core.notifications

import android.app.Application
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.testing.FakeAutomaticBackupRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** One automatic backup: written while the battery allows, tried again later otherwise or when it fails. */
@RunWith(RobolectricTestRunner::class)
class AutomaticBackupWorkerTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val backups = FakeAutomaticBackupRepository()

    private suspend fun runWorker(batteryAllows: Boolean): ListenableWorker.Result {
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = AutomaticBackupWorker(appContext, workerParameters, backups) { batteryAllows }
            }
        return TestListenableWorkerBuilder<AutomaticBackupWorker>(context).setWorkerFactory(factory).build().doWork()
    }

    @Test
    fun `a backup is written while charging or the battery is not low`() =
        runTest {
            assertEquals(ListenableWorker.Result.success(), runWorker(batteryAllows = true))
            assertEquals(1, backups.backUps)
        }

    @Test
    fun `on a low battery it waits for a later try`() =
        runTest {
            assertEquals(ListenableWorker.Result.retry(), runWorker(batteryAllows = false))
            assertEquals(0, backups.backUps)
        }

    @Test
    fun `a backup that cannot be written now is tried again later`() =
        runTest {
            backups.failure = DomainError.FileUnavailable

            assertEquals(ListenableWorker.Result.retry(), runWorker(batteryAllows = true))
        }
}
