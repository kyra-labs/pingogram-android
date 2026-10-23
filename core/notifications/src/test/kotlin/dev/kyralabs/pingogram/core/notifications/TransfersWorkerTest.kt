package dev.kyralabs.pingogram.core.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import dev.kyralabs.pingogram.core.domain.model.Transfers
import dev.kyralabs.pingogram.core.testing.FakeTransferRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration.Companion.milliseconds

/** How long [TransfersWorker] runs: while the user's transfers do, and [TransfersWorker.LINGER] beyond. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TransfersWorkerTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val transfers = FakeTransferRepository()

    @Before
    fun setUp() {
        context.registerLauncherActivity()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun worker(): TransfersWorker {
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = TransfersWorker(appContext, workerParameters, transfers, TransferNotificationBuilder(appContext))
            }
        return TestListenableWorkerBuilder<TransfersWorker>(context).setWorkerFactory(factory).build()
    }

    private fun TestScope.shownProgress(): Int? =
        shadowOf(manager)
            .getNotification(TransferNotificationBuilder.NOTIFICATION_ID)
            ?.extras
            ?.getInt(Notification.EXTRA_PROGRESS)

    @Test
    fun `nothing to transfer ends at once, showing nothing`() =
        runTest {
            assertEquals(ListenableWorker.Result.success(), worker().doWork())
            assertNull(shownProgress())
        }

    @Test
    fun `follows the progress, then ends a little after the last transfer`() =
        runTest {
            transfers.transfers.value = Transfers(uploads = 1, transferredSize = 0, totalSize = 1_000)
            val result = async { worker().doWork() }
            runCurrent()

            transfers.transfers.value = Transfers(uploads = 1, transferredSize = 500, totalSize = 1_000)
            advanceTimeBy(TransfersWorker.UPDATE_INTERVAL + 1.milliseconds)
            assertEquals(500, shownProgress())

            transfers.transfers.value = Transfers.None
            advanceTimeBy(TransfersWorker.LINGER - 1.milliseconds)
            assertFalse(result.isCompleted)
            // Within 5 s (docs/PERFORMANCE.md §6), even if an update of the notification was just shown.
            advanceTimeBy(TransfersWorker.UPDATE_INTERVAL + 2.milliseconds)
            assertTrue(result.isCompleted)
            assertEquals(ListenableWorker.Result.success(), result.await())
        }

    @Test
    fun `a transfer that begins soon after the last one keeps it running`() =
        runTest {
            transfers.transfers.value = Transfers(downloads = 1, totalSize = 1_000)
            val result = async { worker().doWork() }
            runCurrent()

            transfers.transfers.value = Transfers.None
            advanceTimeBy(TransfersWorker.LINGER / 2)
            transfers.transfers.value = Transfers(uploads = 1, totalSize = 1_000)
            advanceTimeBy(TransfersWorker.LINGER * 2)

            assertFalse(result.isCompleted)
            transfers.cancelAll()
            advanceTimeBy(TransfersWorker.UPDATE_INTERVAL + TransfersWorker.LINGER + 1.milliseconds)
            assertTrue(result.isCompleted)
        }
}
