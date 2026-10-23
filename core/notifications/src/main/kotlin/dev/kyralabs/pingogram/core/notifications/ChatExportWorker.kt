package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChatExportError
import dev.kyralabs.pingogram.core.domain.model.ChatExportProgress
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatExportOutcome
import dev.kyralabs.pingogram.core.domain.repository.ChatExporter
import dev.kyralabs.pingogram.core.domain.repository.ExportFolderRepository
import dev.kyralabs.pingogram.core.domain.repository.awaitLoggedIn
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Exports one chat in the background (`REQUIREMENTS.md` §5.9, `docs/adr/0021-chat-export.md`): [ChatExporter] in
 * WorkManager's foreground service of type `dataSync`, as the user's transfers run ([TransfersWorker]), so it goes on
 * after the export page closed and while the app is in the background. Its notification
 * ([ChatExportNotificationBuilder]) shows how far it is, at most once per [UPDATE_INTERVAL], and cancels it through
 * WorkManager ([WorkManager.createCancelPendingIntent]); the page reads the same progress from the work's progress
 * data ([WorkManagerChatExportJobs]).
 *
 * Started expedited from the export page, so it enters the foreground at once. After process death WorkManager runs it
 * again from the start; it waits for TDLib to log in first. Cancelled, the exporter deletes what it wrote. Either way
 * the folder's grant is given back at the end ([ExportFolderRepository.release]).
 */
@HiltWorker
class ChatExportWorker
    @AssistedInject
    internal constructor(
        @Assisted context: Context,
        @Assisted parameters: WorkerParameters,
        private val authRepository: AuthRepository,
        private val exporter: ChatExporter,
        private val folders: ExportFolderRepository,
        private val builder: ChatExportNotificationBuilder,
    ) : CoroutineWorker(context, parameters) {
        private val input by lazy { ChatExportInput.from(inputData) }

        override suspend fun doWork(): Result {
            val input = input ?: return Result.failure(ChatExportError.FolderUnavailable.toData())
            return try {
                export(input)
            } finally {
                withContext(NonCancellable) { folders.release(input.options.folderUri) }
            }
        }

        /** Exports as [input] says, once TDLib is logged in, and says how it ended. */
        private suspend fun export(input: ChatExportInput): Result {
            val notificationId = ChatExportNotificationBuilder.notificationId(input.chatId)
            enterForeground(foregroundInfo(input, ChatExportProgress()))
            val loggedIn = withTimeoutOrNull(LOGIN_TIMEOUT) { authRepository.awaitLoggedIn() } == true
            val outcome =
                if (loggedIn) {
                    exportLoggedIn(input, notificationId)
                } else {
                    ChatExportOutcome.Failure(ChatExportError.Telegram(DomainError.Unauthorized))
                }
            return when (outcome) {
                is ChatExportOutcome.Success -> {
                    builder.finished(notificationId, input.chatTitle, outcome.result)
                    Result.success(outcome.result.toData())
                }

                is ChatExportOutcome.Failure -> {
                    builder.finished(notificationId, input.chatTitle, null)
                    Result.failure(outcome.error.toData())
                }
            }
        }

        /** The export itself, its progress shown at most once per [UPDATE_INTERVAL]. */
        private suspend fun exportLoggedIn(
            input: ChatExportInput,
            notificationId: Int,
        ): ChatExportOutcome {
            var shown = TimeSource.Monotonic.markNow() - UPDATE_INTERVAL
            val texts = ResourceExportTexts(applicationContext)
            return exporter.export(input.chatId, input.options, texts) { progress ->
                // A flood wait shows at once; reading only as often as Android redraws notifications.
                if (shown.elapsedNow() >= UPDATE_INTERVAL || progress.floodWaitSeconds != null) {
                    shown = TimeSource.Monotonic.markNow()
                    setProgress(progress.toData())
                    builder.update(notificationId, builder.running(input.chatTitle, progress, cancelIntent()))
                }
            }
        }

        /** Shows [info] as the foreground service's notification; Android 12+ refuses it from the background. */
        private suspend fun enterForeground(info: ForegroundInfo) {
            try {
                setForeground(info)
            } catch (refused: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException: it runs on while Android keeps the process.
                Log.w(TAG, "Export not in the foreground: ${refused.javaClass.simpleName}")
            }
        }

        /** Before Android 12, expedited work runs in WorkManager's foreground service from the start. */
        override suspend fun getForegroundInfo(): ForegroundInfo =
            foregroundInfo(checkNotNull(input), ChatExportProgress())

        private fun foregroundInfo(
            input: ChatExportInput,
            progress: ChatExportProgress,
        ): ForegroundInfo {
            val notification = builder.running(input.chatTitle, progress, cancelIntent())
            val id = ChatExportNotificationBuilder.notificationId(input.chatId)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(id, notification)
            }
        }

        private fun cancelIntent() = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)

        internal companion object {
            /** How often the notification and the page see the progress anew. */
            val UPDATE_INTERVAL = 1.seconds

            /** How long it waits for TDLib to log in after a restart. */
            val LOGIN_TIMEOUT = 30.seconds

            private const val TAG = "ChatExport"
        }
    }
