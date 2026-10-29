package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.domain.model.ChatExportJob
import dev.kyralabs.pingogram.core.domain.model.ChatExportOptions
import dev.kyralabs.pingogram.core.domain.model.ChatExportStatus
import dev.kyralabs.pingogram.core.domain.repository.ChatExportJobs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ChatExportJobs] on WorkManager: unique work per chat (`chat_export_<id>`) of [ChatExportWorker], expedited (it
 * runs as normal work when Android's quota for expedited work is used up), kept while one runs. Its state, progress
 * and output data are what [job] reports; WorkManager keeps finished work for a day.
 */
@Singleton
class WorkManagerChatExportJobs
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : ChatExportJobs {
        private val workManager: WorkManager get() = WorkManager.getInstance(context)

        override fun start(
            chatId: Long,
            chatTitle: String,
            options: ChatExportOptions,
        ) {
            val request =
                OneTimeWorkRequestBuilder<ChatExportWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setInputData(ChatExportInput(chatId, chatTitle, options).toData())
                    .addTag(TAG)
                    .build()
            workManager.enqueueUniqueWork(workName(chatId), ExistingWorkPolicy.KEEP, request)
        }

        override fun cancel(chatId: Long) {
            workManager.cancelUniqueWork(workName(chatId))
        }

        override fun job(chatId: Long): Flow<ChatExportJob?> =
            workManager
                .getWorkInfosForUniqueWorkFlow(workName(chatId))
                .map { infos -> infos.lastOrNull()?.toJob() }
                .distinctUntilChanged()

        internal companion object {
            /** Every export's tag, by which the storage clean-up waits for them ([StorageOptimizationWorker]). */
            const val TAG = "chat_export"

            fun workName(chatId: Long) = "chat_export_$chatId"
        }
    }

/** What became of the export of this work. */
internal fun WorkInfo.toJob(): ChatExportJob =
    ChatExportJob(
        id = id.toString(),
        status =
            when (state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> {
                    ChatExportStatus.Running(progressOf(progress))
                }

                WorkInfo.State.SUCCEEDED -> {
                    ChatExportStatus.Done(resultOf(outputData))
                }

                WorkInfo.State.FAILED -> {
                    ChatExportStatus.Failed(errorOf(outputData))
                }

                WorkInfo.State.CANCELLED -> {
                    ChatExportStatus.Cancelled
                }
            },
    )
