package dev.kyralabs.pingogram.feature.chat.ui.export

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChatExportError
import dev.kyralabs.pingogram.core.domain.model.ChatExportJob
import dev.kyralabs.pingogram.core.domain.model.ChatExportOptions
import dev.kyralabs.pingogram.core.domain.model.ChatExportProgress
import dev.kyralabs.pingogram.core.domain.model.ChatExportResult
import dev.kyralabs.pingogram.core.domain.model.ChatExportStatus
import dev.kyralabs.pingogram.core.domain.model.ExportChatKind
import dev.kyralabs.pingogram.core.domain.model.ExportFormat
import dev.kyralabs.pingogram.core.domain.model.ExportMediaKind
import dev.kyralabs.pingogram.core.domain.repository.ChatExportAccess
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * The export page's view model: reading the chat, the form and its saved state, the folder's grant, starting,
 * following and cancelling the export, and what the page shows once it ended.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatExportViewModelTest {
    private val repository = FakeChatExportRepository()
    private val jobs = FakeChatExportJobs()
    private val folders = FakeExportFolders()

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(savedState: SavedStateHandle = SavedStateHandle()): ChatExportViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel =
            ChatExportViewModel(
                chatId = 100,
                savedState = savedState,
                exportRepository = repository,
                jobs = jobs,
                folders = folders,
                applicationScope = backgroundScope,
                zone = { ZoneOffset.UTC },
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private val ChatExportViewModel.content get() = uiState.value as ChatExportUiState.Content

    private fun TestScope.send(
        viewModel: ChatExportViewModel,
        vararg events: ChatExportEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    private fun job(
        status: ChatExportStatus,
        id: String = "run-1",
    ) = ChatExportJob(id, status)

    @Test
    fun `loads the chat after the first frame, then shows the default form without a folder`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val viewModel = viewModel()

            assertEquals(1, repository.chatRequests)
            assertEquals(UiText.Plain("Ada Lovelace"), viewModel.content.chatTitle)
            assertEquals(ExportForm(), viewModel.content.form)
            assertNull(viewModel.content.run)
            assertFalse(viewModel.content.form.canExport)
        }

    @Test
    fun `Saved Messages is named so`() =
        runTest {
            repository.chat = repository.chat.copy(kind = ExportChatKind.SavedMessages)

            assertEquals(UiText.resource(R.string.chat_saved_messages), viewModel().content.chatTitle)
        }

    @Test
    fun `a secret chat and one with protected content cannot be exported`() =
        runTest {
            repository.access = ChatExportAccess.SecretChat
            assertEquals(ChatExportUiState.NotAllowed(isSecretChat = true), viewModel().uiState.value)
            repository.access = ChatExportAccess.ProtectedContent
            assertEquals(ChatExportUiState.NotAllowed(isSecretChat = false), viewModel().uiState.value)
        }

    @Test
    fun `a chat that cannot be read shows why, and is read again on Try again`() =
        runTest {
            for (error in listOf(DomainError.Network, DomainError.Unauthorized, DomainError.FloodWait(30))) {
                repository.error = error
                val viewModel = viewModel()
                assertEquals(ChatExportUiState.Error(error), viewModel.uiState.value)

                repository.error = null
                send(viewModel, ChatExportEvent.Retry)
                assertTrue(viewModel.uiState.value is ChatExportUiState.Content)
            }
        }

    @Test
    fun `each choice changes the form`() =
        runTest {
            val viewModel = viewModel()

            send(
                viewModel,
                ChatExportEvent.SetFormat(ExportFormat.Json),
                ChatExportEvent.SetMedia(ExportMediaKind.Photos, true),
                ChatExportEvent.SetMedia(ExportMediaKind.Files, true),
                ChatExportEvent.SetMedia(ExportMediaKind.Photos, false),
                ChatExportEvent.SetSizeLimit(2),
                ChatExportEvent.Since(20_000),
            )

            assertEquals(
                ExportForm(
                    format = ExportFormat.Json,
                    media = persistentSetOf(ExportMediaKind.Files),
                    sizeLimit = 2,
                    sinceDay = 20_000,
                ),
                viewModel.content.form,
            )
            send(viewModel, ChatExportEvent.AllMessages, ChatExportEvent.SetSizeLimit(99))
            assertNull(viewModel.content.form.sinceDay)
            assertEquals(ExportSizeLimits.lastIndex, viewModel.content.form.sizeLimit)
        }

    @Test
    fun `a folder picked is kept and named, and another gives the first one back`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, ChatExportEvent.FolderPicked("content://tree/download"))
            assertEquals("Download", viewModel.content.form.folderName)
            assertTrue(viewModel.content.form.canExport)

            send(viewModel, ChatExportEvent.FolderPicked("content://tree/documents"))
            assertEquals("Documents", viewModel.content.form.folderName)
            assertEquals(listOf("content://tree/download"), folders.released)
        }

    @Test
    fun `a folder that cannot be used says so and keeps the form as it was`() =
        runTest {
            val viewModel = viewModel()

            viewModel.effects.test {
                send(viewModel, ChatExportEvent.FolderPicked("content://tree/gone"))
                assertEquals(ChatExportEffect.FolderUnusable, awaitItem())
            }
            assertNull(viewModel.content.form.folderName)
            assertFalse(viewModel.content.form.isPickingFolder)
        }

    @Test
    fun `Export starts the job with the form's options, the day from its start in the user's time zone`() =
        runTest {
            val viewModel = viewModel()
            send(
                viewModel,
                ChatExportEvent.SetFormat(ExportFormat.Json),
                ChatExportEvent.SetMedia(ExportMediaKind.VoiceMessages, true),
                ChatExportEvent.SetSizeLimit(1),
                ChatExportEvent.Since(LocalDate.of(2026, 10, 1).toEpochDay()),
                ChatExportEvent.FolderPicked("content://tree/download"),
                ChatExportEvent.Export("Ada Lovelace"),
            )

            val (chatId, title, options) = jobs.started.single()
            assertEquals(100L, chatId)
            assertEquals("Ada Lovelace", title)
            assertEquals(
                ChatExportOptions(
                    format = ExportFormat.Json,
                    media = persistentSetOf(ExportMediaKind.VoiceMessages),
                    maxFileSize = ExportSizeLimits[1],
                    since = LocalDate.of(2026, 10, 1).atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
                    folderUri = "content://tree/download",
                ),
                options,
            )
            // The worker holds the folder now.
            assertNull(viewModel.content.form.folderName)
            assertTrue(folders.released.isEmpty())
        }

    @Test
    fun `without a folder nothing starts`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, ChatExportEvent.Export("Ada"))

            assertTrue(jobs.started.isEmpty())
        }

    @Test
    fun `follows the export it started, then shows its result until dismissed`() =
        runTest {
            val viewModel = viewModel()
            send(viewModel, ChatExportEvent.FolderPicked("content://tree/download"), ChatExportEvent.Export("Ada"))

            jobs.job.value = job(ChatExportStatus.Running(ChatExportProgress(messages = 10, fraction = 0.5f)))
            runCurrent()
            assertEquals(ExportRun.Running(ChatExportProgress(messages = 10, fraction = 0.5f)), viewModel.content.run)

            val result = ChatExportResult(1_234, 5, 2, "content://doc/export")
            jobs.job.value = job(ChatExportStatus.Done(result))
            runCurrent()
            assertEquals(ExportRun.Done(result), viewModel.content.run)

            send(viewModel, ChatExportEvent.Dismiss)
            assertNull(viewModel.content.run)
        }

    @Test
    fun `a failed export shows why until Try again returns to the form`() =
        runTest {
            val viewModel = viewModel()
            send(viewModel, ChatExportEvent.FolderPicked("content://tree/download"), ChatExportEvent.Export("Ada"))

            for (
            error in
            listOf(
                ChatExportError.FolderUnavailable,
                ChatExportError.StorageFull,
                ChatExportError.Telegram(DomainError.FloodWait(60)),
                ChatExportError.Telegram(DomainError.Network),
                ChatExportError.Telegram(DomainError.Unauthorized),
            )
            ) {
                jobs.job.value = job(ChatExportStatus.Failed(error), id = error.toString())
                runCurrent()
                assertEquals(ExportRun.Failed(error), viewModel.content.run)
                send(viewModel, ChatExportEvent.Dismiss)
                assertNull(viewModel.content.run)
            }
        }

    @Test
    fun `an export that ended before the page opened is not shown, one running is, and followed to its end`() =
        runTest {
            jobs.job.value = job(ChatExportStatus.Done(ChatExportResult(1, 0, 0, "")), id = "old")
            val viewModel = viewModel()
            assertNull(viewModel.content.run)

            jobs.job.value = job(ChatExportStatus.Running(ChatExportProgress()), id = "new")
            runCurrent()
            assertEquals(ExportRun.Running(ChatExportProgress()), viewModel.content.run)
            jobs.job.value = job(ChatExportStatus.Failed(ChatExportError.StorageFull), id = "new")
            runCurrent()
            assertEquals(ExportRun.Failed(ChatExportError.StorageFull), viewModel.content.run)
        }

    @Test
    fun `starting again hides the last result until the new export reports`() =
        runTest {
            val viewModel = viewModel()
            send(viewModel, ChatExportEvent.FolderPicked("content://tree/download"), ChatExportEvent.Export("Ada"))
            jobs.job.value = job(ChatExportStatus.Done(ChatExportResult(1, 0, 0, "")))
            runCurrent()

            send(viewModel, ChatExportEvent.Dismiss, ChatExportEvent.FolderPicked("content://tree/download"))
            send(viewModel, ChatExportEvent.Export("Ada"))

            assertNull(viewModel.content.run)
            assertEquals(2, jobs.started.size)
        }

    @Test
    fun `Cancel cancels the running export and says so, then the form shows again`() =
        runTest {
            val viewModel = viewModel()
            send(viewModel, ChatExportEvent.FolderPicked("content://tree/download"), ChatExportEvent.Export("Ada"))
            jobs.job.value = job(ChatExportStatus.Running(ChatExportProgress()))
            runCurrent()

            viewModel.effects.test {
                send(viewModel, ChatExportEvent.Cancel)
                assertEquals(ChatExportEffect.Cancelled, awaitItem())
            }
            assertEquals(listOf(100L), jobs.cancelled)
            jobs.job.value = job(ChatExportStatus.Cancelled)
            runCurrent()
            assertNull(viewModel.content.run)
        }

    @Test
    fun `Cancel without a running export does nothing`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, ChatExportEvent.Cancel)

            assertTrue(jobs.cancelled.isEmpty())
        }

    @Test
    fun `the form, the folder and what the page follows survive process death`() =
        runTest {
            val savedState = SavedStateHandle()
            val first = viewModel(savedState)
            send(
                first,
                ChatExportEvent.SetFormat(ExportFormat.Json),
                ChatExportEvent.SetMedia(ExportMediaKind.Videos, true),
                ChatExportEvent.SetSizeLimit(3),
                ChatExportEvent.Since(20_100),
                ChatExportEvent.FolderPicked("content://tree/download"),
            )

            // A new view model on what the saved state kept, as after process death.
            val restored = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any?>(it) })
            val second = viewModel(restored)

            assertEquals(first.content.form, second.content.form)
            send(second, ChatExportEvent.Export("Ada"))
            assertEquals(
                "content://tree/download",
                jobs.started
                    .single()
                    .third.folderUri,
            )
            jobs.job.value = job(ChatExportStatus.Done(ChatExportResult(1, 0, 0, "")))
            runCurrent()
            val third = viewModel(SavedStateHandle(restored.keys().associateWith { restored.get<Any?>(it) }))
            assertEquals(ExportRun.Done(ChatExportResult(1, 0, 0, "")), third.content.run)
        }

    @Test
    fun `leaving the page gives back a folder picked but not exported into`() =
        runTest {
            val viewModel = viewModel()
            send(viewModel, ChatExportEvent.FolderPicked("content://tree/download"))

            clear(viewModel)
            runCurrent()

            assertEquals(listOf("content://tree/download"), folders.released)
        }

    @Test
    fun `leaving the page while exporting keeps the folder for the export`() =
        runTest {
            val viewModel = viewModel()
            send(viewModel, ChatExportEvent.FolderPicked("content://tree/download"), ChatExportEvent.Export("Ada"))

            clear(viewModel)
            runCurrent()

            assertTrue(folders.released.isEmpty())
        }

    /** Clears [viewModel] as leaving the page does, through a [ViewModelStore]. */
    private fun clear(viewModel: ChatExportViewModel) {
        val store = ViewModelStore()
        val factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: KClass<T>,
                    extras: CreationExtras,
                ): T = modelClass.cast(viewModel)
            }
        ViewModelProvider.create(store, factory)[ChatExportViewModel::class]
        store.clear()
    }
}
