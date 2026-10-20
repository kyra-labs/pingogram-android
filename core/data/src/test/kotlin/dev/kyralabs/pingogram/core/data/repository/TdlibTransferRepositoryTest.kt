package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.Transfers
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.authorizationStateUpdate
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.closed
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.document
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.failedState
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.file
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageDocument
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.pendingState
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateDeleteMessages
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateFile
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateMessageSendFailed
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateMessageSendSucceeded
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Which uploads and downloads [TdlibTransferRepository] counts as the user's transfers, and how it cancels them. */
@OptIn(ExperimentalCoroutinesApi::class)
class TdlibTransferRepositoryTest {
    private val client =
        FakeTdlibClient().apply {
            on(TdApi.DownloadFile::class) { request -> TdResult.Success(downloading(request.fileId, 0)) }
            on(TdApi.CancelDownloadFile::class, TdResult.Success(TdApi.Ok()))
            on(TdApi.DeleteMessages::class, TdResult.Success(TdApi.Ok()))
        }

    private fun TestScope.repository() = TdlibTransferRepository(client, backgroundScope).also { runCurrent() }

    private suspend fun TestScope.emit(update: TdApi.Update) {
        client.emit(update)
        runCurrent()
    }

    @Test
    fun `a document being sent is an upload until it is sent`() =
        runTest {
            val repository = repository()

            emit(updateNewMessage(sending(id = 1, uploading(UPLOAD_FILE, uploadedSize = 0))))
            assertEquals(Transfers(uploads = 1, totalSize = SIZE), repository.transfers.value)

            emit(updateFile(uploading(UPLOAD_FILE, uploadedSize = 300)))
            assertEquals(Transfers(uploads = 1, transferredSize = 300, totalSize = SIZE), repository.transfers.value)

            emit(updateMessageSendSucceeded(message(id = 2, isOutgoing = true), oldMessageId = 1))
            assertEquals(Transfers.None, repository.transfers.value)
        }

    @Test
    fun `an upload ends when its message fails or is deleted`() =
        runTest {
            val repository = repository()
            emit(updateNewMessage(sending(id = 1, uploading(UPLOAD_FILE, uploadedSize = 0))))
            emit(updateNewMessage(sending(id = 2, uploading(UPLOAD_FILE + 1, uploadedSize = 0))))
            assertEquals(2, repository.transfers.value.uploads)

            emit(updateMessageSendFailed(message(id = 3, isOutgoing = true, sendingState = failedState()), 1))
            emit(updateDeleteMessages(CHAT_ID, 2))

            assertEquals(Transfers.None, repository.transfers.value)
        }

    @Test
    fun `forwarded files, received messages and text count for nothing`() =
        runTest {
            val repository = repository()

            emit(updateNewMessage(sending(id = 1, file(id = UPLOAD_FILE))))
            emit(updateNewMessage(message(id = 2, content = messageDocument(document(file = uploading(UPLOAD_FILE))))))
            emit(updateNewMessage(message(id = 3, isOutgoing = true, sendingState = pendingState())))

            assertTrue(repository.transfers.value.isEmpty)
        }

    @Test
    fun `a requested download counts until it completes`() =
        runTest {
            val repository = repository()

            assertEquals(Outcome.Success(Unit), repository.download(DOWNLOAD_FILE, priority = 32))
            runCurrent()
            assertEquals(Transfers(downloads = 1, totalSize = SIZE), repository.transfers.value)

            emit(updateFile(downloading(DOWNLOAD_FILE, downloadedSize = 512)))
            assertEquals(Transfers(downloads = 1, transferredSize = 512, totalSize = SIZE), repository.transfers.value)

            emit(
                updateFile(file(id = DOWNLOAD_FILE, size = SIZE, downloadedSize = SIZE, isDownloadingCompleted = true)),
            )
            assertEquals(Transfers.None, repository.transfers.value)
            assertEquals(32, client.sentOf<TdApi.DownloadFile>().single().priority)
        }

    @Test
    fun `a download ends when it stops, and is not followed when TDLib refuses it`() =
        runTest {
            val repository = repository()
            repository.download(DOWNLOAD_FILE, priority = 32)

            emit(updateFile(file(id = DOWNLOAD_FILE, size = SIZE, downloadedSize = 512, isDownloadingActive = false)))
            assertEquals(Transfers.None, repository.transfers.value)

            client.on(TdApi.DownloadFile::class, TdResult.Failure(TdError(400, "FILE_ID_INVALID")))
            assertTrue(repository.download(DOWNLOAD_FILE, priority = 32) is Outcome.Failure)
            assertEquals(Transfers.None, repository.transfers.value)
        }

    @Test
    fun `a download that completes before TDLib answers is not followed`() =
        runTest {
            val answer = CompletableDeferred<TdResult<TdApi.File>>()
            client.on(TdApi.DownloadFile::class) { answer.await() }
            val repository = repository()
            val download = backgroundScope.launch { repository.download(DOWNLOAD_FILE, priority = 32) }
            runCurrent()

            emit(
                updateFile(file(id = DOWNLOAD_FILE, size = SIZE, downloadedSize = SIZE, isDownloadingCompleted = true)),
            )
            answer.complete(TdResult.Success(downloading(DOWNLOAD_FILE, downloadedSize = 0)))
            download.join()

            assertEquals(Transfers.None, repository.transfers.value)
        }

    @Test
    fun `only downloads the user requested are transfers`() =
        runTest {
            val transfers = repository()
            val files = TdlibFileRepository(client, transfers, backgroundScope)

            files.download(DOWNLOAD_FILE, DownloadPriority.Visible)
            runCurrent()
            assertTrue(transfers.transfers.value.isEmpty)

            files.download(DOWNLOAD_FILE + 1, DownloadPriority.Requested)
            runCurrent()
            assertEquals(1, transfers.transfers.value.downloads)
        }

    @Test
    fun `cancelling stops the downloads and deletes the messages being sent, per chat`() =
        runTest {
            val repository = repository()
            repository.download(DOWNLOAD_FILE, priority = 32)
            emit(updateNewMessage(sending(id = 1, uploading(UPLOAD_FILE))))
            emit(updateNewMessage(sending(id = 2, uploading(UPLOAD_FILE + 1))))

            assertEquals(Outcome.Success(Unit), repository.cancelAll())

            assertEquals(DOWNLOAD_FILE, client.sentOf<TdApi.CancelDownloadFile>().single().fileId)
            val deleted = client.sentOf<TdApi.DeleteMessages>().single()
            assertEquals(CHAT_ID, deleted.chatId)
            assertEquals(listOf(1L, 2L), deleted.messageIds.toList())
            assertTrue(deleted.revoke)
        }

    @Test
    fun `cancelling tries everything and reports a failure`() =
        runTest {
            client.on(TdApi.CancelDownloadFile::class, TdResult.Failure(TdError(400, "FILE_ID_INVALID")))
            val repository = repository()
            repository.download(DOWNLOAD_FILE, priority = 32)
            emit(updateNewMessage(sending(id = 1, uploading(UPLOAD_FILE))))

            assertTrue(repository.cancelAll() is Outcome.Failure)
            assertEquals(1, client.sentOf<TdApi.DeleteMessages>().size)
        }

    @Test
    fun `logging out ends every transfer`() =
        runTest {
            val repository = repository()
            emit(updateNewMessage(sending(id = 1, uploading(UPLOAD_FILE))))

            emit(authorizationStateUpdate(closed()))

            assertEquals(Transfers.None, repository.transfers.value)
        }

    private fun sending(
        id: Long,
        file: TdApi.File,
    ) = message(
        id = id,
        chatId = CHAT_ID,
        isOutgoing = true,
        sendingState = pendingState(),
        content = messageDocument(document(file = file)),
    )

    private fun uploading(
        fileId: Int,
        uploadedSize: Long = 0,
    ) = file(id = fileId, size = SIZE, localPath = "/cache/uploads/a/report.pdf", isDownloadingCompleted = true).apply {
        remote = TdApi.RemoteFile("", "", true, false, uploadedSize)
    }

    private fun downloading(
        fileId: Int,
        downloadedSize: Long,
    ) = file(id = fileId, size = SIZE, downloadedSize = downloadedSize, isDownloadingActive = true)

    private companion object {
        const val CHAT_ID = 100L
        const val UPLOAD_FILE = 7
        const val DOWNLOAD_FILE = 40
        const val SIZE = 1_000L
    }
}
