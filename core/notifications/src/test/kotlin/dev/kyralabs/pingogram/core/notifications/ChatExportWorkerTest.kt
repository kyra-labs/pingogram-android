package dev.kyralabs.pingogram.core.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatExportError
import dev.kyralabs.pingogram.core.domain.model.ChatExportOptions
import dev.kyralabs.pingogram.core.domain.model.ChatExportProgress
import dev.kyralabs.pingogram.core.domain.model.ChatExportResult
import dev.kyralabs.pingogram.core.domain.model.ExportFormat
import dev.kyralabs.pingogram.core.domain.model.ExportMediaKind
import dev.kyralabs.pingogram.core.domain.repository.ChatExportOutcome
import dev.kyralabs.pingogram.core.domain.repository.ChatExporter
import dev.kyralabs.pingogram.core.domain.repository.ExportFolderRepository
import dev.kyralabs.pingogram.core.domain.repository.ExportTexts
import dev.kyralabs.pingogram.core.tdlib.AuthState
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * [ChatExportWorker] with a fake [ChatExporter]: the options it gets from its input, its result and errors as output
 * data, the notification that it ended, and the folder's grant given back whatever happened.
 */
@RunWith(RobolectricTestRunner::class)
class ChatExportWorkerTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val exporter = FakeExporter()
    private val folders = FakeFolders()

    private val options =
        ChatExportOptions(
            format = ExportFormat.Json,
            media = persistentSetOf(ExportMediaKind.Photos, ExportMediaKind.Files),
            maxFileSize = 50_000_000,
            since = 1_700_000_000,
            folderUri = "content://tree/download",
        )

    @Before
    fun setUp() {
        context.registerLauncherActivity()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        // The notification's "Cancel" is WorkManager's own pending intent.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private fun TestScope.worker(): ChatExportWorker {
        val harness = NotificationsHarness(this).apply { client.authState.value = AuthState.Ready }
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = ChatExportWorker(
                    appContext,
                    workerParameters,
                    harness.auth,
                    exporter,
                    folders,
                    ChatExportNotificationBuilder(appContext),
                )
            }
        return TestListenableWorkerBuilder<ChatExportWorker>(context, ChatExportInput(100, "Ada", options).toData())
            .setWorkerFactory(factory)
            .build()
    }

    @Test
    fun `exports the chat with the options of its input, then says so and gives the folder back`() =
        runTest {
            exporter.outcome = ChatExportOutcome.Success(ChatExportResult(12, 3, 1, "content://doc/export"))

            val result = worker().doWork()

            assertEquals(100L, exporter.chatId)
            assertEquals(options, exporter.options)
            assertEquals(ListenableWorker.Result.success(resultData()), result)
            assertEquals(listOf("content://tree/download"), folders.released)
            val finished =
                shadowOf(manager).getNotification(
                    ChatExportNotificationBuilder.FINISHED_TAG,
                    ChatExportNotificationBuilder.notificationId(100),
                )
            assertEquals(
                "Ada exported",
                shadowOf(finished).contentTitle.toString().filter { it.isLetter() || it == ' ' },
            )
        }

    @Test
    fun `a failure is its output, the folder given back too`() =
        runTest {
            for (
            error in
            listOf(
                ChatExportError.FolderUnavailable,
                ChatExportError.StorageFull,
                ChatExportError.NotAllowed,
                ChatExportError.Telegram(DomainError.Network),
                ChatExportError.Telegram(DomainError.Unauthorized),
                ChatExportError.Telegram(DomainError.FloodWait(30)),
            )
            ) {
                exporter.outcome = ChatExportOutcome.Failure(error)

                val result = worker().doWork()

                assertEquals(ListenableWorker.Result.failure(error.toData()), result)
                assertEquals(error, errorOf((result as ListenableWorker.Result.Failure).outputData))
            }
            assertEquals(6, folders.released.size)
        }

    @Test
    fun `cancelled, it gives the folder back`() =
        runTest {
            exporter.block = true

            val work = async { worker().doWork() }
            runCurrent()
            work.cancel()
            runCurrent()

            assertTrue(work.isCancelled)
            assertEquals(listOf("content://tree/download"), folders.released)
        }

    @Test
    fun `progress, results and errors survive WorkManager's data`() {
        val progress = ChatExportProgress(messages = 1_234, fraction = 0.25f, floodWaitSeconds = 12)
        assertEquals(progress, progressOf(progress.toData()))
        assertEquals(ChatExportProgress(), progressOf(ChatExportProgress().toData()))
        assertEquals(
            ChatExportInput(100, "Ada", options),
            ChatExportInput.from(ChatExportInput(100, "Ada", options).toData()),
        )
        val all = options.copy(media = persistentSetOf(), since = null)
        assertEquals(all, ChatExportInput.from(ChatExportInput(100, "Ada", all).toData())?.options)
    }

    private fun resultData() = ChatExportResult(12, 3, 1, "content://doc/export").toData()

    private class FakeExporter : ChatExporter {
        var outcome: ChatExportOutcome = ChatExportOutcome.Failure(ChatExportError.FolderUnavailable)
        var block = false
        var chatId: Long? = null
        var options: ChatExportOptions? = null

        override suspend fun export(
            chatId: Long,
            options: ChatExportOptions,
            texts: ExportTexts,
            onProgress: suspend (ChatExportProgress) -> Unit,
        ): ChatExportOutcome {
            this.chatId = chatId
            this.options = options
            onProgress(ChatExportProgress(messages = 1))
            if (block) CompletableDeferred<Unit>().await()
            return outcome
        }
    }

    private class FakeFolders : ExportFolderRepository {
        val released = mutableListOf<String>()

        override suspend fun keep(treeUri: String): Outcome<String> = Outcome.Success("Download")

        override suspend fun release(treeUri: String) {
            released += treeUri
        }
    }
}
