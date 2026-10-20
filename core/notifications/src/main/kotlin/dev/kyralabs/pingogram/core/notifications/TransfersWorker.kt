package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.kyralabs.pingogram.core.domain.model.Transfers
import dev.kyralabs.pingogram.core.domain.repository.TransferRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the app running while the transfers the user started are on their way (`REQUIREMENTS.md` FR-N4,
 * `docs/PERFORMANCE.md` §6): a foreground service of type `dataSync` (WorkManager's), with the transfers' progress as
 * its notification ([TransferNotificationBuilder]). Downloads TDLib or the app start by themselves never start it.
 *
 * - [start] runs it, by the app's `TransfersController`, as transfers begin while the app is in the foreground:
 *   expedited, so it starts at once and promotes itself to the foreground before the user leaves. Android 12+ refuses a
 *   foreground service started from the background; then it ends, and the transfers continue for as long as Android
 *   keeps the process.
 * - It updates the notification at most once per [UPDATE_INTERVAL] (Android drops faster updates), and ends
 *   [LINGER] after the last transfer ended, so a transfer that follows at once keeps the same notification.
 * - Android 15+ allows `dataSync` 6 hours a day; when they are up, WorkManager's service receives `onTimeout`, stops
 *   this worker and leaves the foreground. It starts again only from the foreground.
 */
@HiltWorker
class TransfersWorker
    @AssistedInject
    internal constructor(
        @Assisted context: Context,
        @Assisted parameters: WorkerParameters,
        private val transferRepository: TransferRepository,
        private val builder: TransferNotificationBuilder,
    ) : CoroutineWorker(context, parameters) {
        @OptIn(ExperimentalCoroutinesApi::class)
        override suspend fun doWork(): Result {
            val first = transferRepository.transfers.value
            if (first.isEmpty) return Result.success()
            try {
                setForeground(foregroundInfo(first))
            } catch (refused: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (Android 12+): the app went to the background meanwhile.
                Log.w(TAG, "Transfers not in the foreground: ${refused.javaClass.simpleName}")
                return Result.success()
            }
            transferRepository.transfers
                .transformLatest { transfers ->
                    // An end waits, and a transfer that begins meanwhile cancels the wait.
                    if (transfers.isEmpty) delay(LINGER)
                    emit(transfers)
                }.takeWhile { !it.isEmpty }
                .conflate()
                .collect { transfers ->
                    builder.update(transfers)
                    delay(UPDATE_INTERVAL)
                }
            return Result.success()
        }

        /** Before Android 12, expedited work runs in WorkManager's foreground service from the start. */
        override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(transferRepository.transfers.value)

        private fun foregroundInfo(transfers: Transfers): ForegroundInfo {
            val notification = builder.build(transfers)
            val id = TransferNotificationBuilder.NOTIFICATION_ID
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(id, notification)
            }
        }

        companion object {
            /** How often the notification shows the progress anew. */
            internal val UPDATE_INTERVAL: Duration = 1.seconds

            /** How long it waits after the last transfer ended before it ends, within the 5 s of PERFORMANCE §6. */
            internal val LINGER: Duration = 2.seconds

            /** The unique work's name: one worker at a time, the next one queued behind it. */
            internal const val WORK_NAME = "transfers"

            private const val TAG = "Transfers"

            /**
             * Runs the worker, or queues one behind the running worker, which may be about to end. Only from the
             * foreground: Android 12+ lets the worker enter the foreground only then.
             */
            fun start(context: Context) {
                val request =
                    OneTimeWorkRequestBuilder<TransfersWorker>()
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                WorkManager
                    .getInstance(context)
                    .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
            }
        }
    }
