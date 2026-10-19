package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FileUpload
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.file
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TdlibFileRepositoryTest {
    private val client =
        FakeTdlibClient().apply {
            on(TdApi.GetFile::class) { request -> TdResult.Success(file(id = request.fileId, size = SIZE)) }
        }

    private fun TestScope.repository() = TdlibFileRepository(client, backgroundScope)

    private fun downloading(downloadedSize: Long) =
        file(id = FILE_ID, size = SIZE, downloadedSize = downloadedSize, isDownloadingActive = true)

    private fun uploading(
        fileId: Int,
        uploadedSize: Long,
        isActive: Boolean,
    ) = file(
        id = fileId,
        size = SIZE,
        localPath = PATH,
        isDownloadingCompleted = true,
        uploadedSize = uploadedSize,
        isUploadingActive = isActive,
        isUploadingCompleted = false,
    )

    private fun downloaded() =
        file(id = FILE_ID, size = SIZE, localPath = PATH, downloadedSize = SIZE, isDownloadingCompleted = true)

    @Test
    fun `observe asks TDLib for a file it does not know yet`() =
        runTest {
            val state = repository().observe(FILE_ID).first()

            assertEquals(FILE_ID, client.sentOf<TdApi.GetFile>().single().fileId)
            assertEquals(FileState.NotDownloaded(size = SIZE, canBeDownloaded = true), state)
        }

    @Test
    fun `observe follows the updates of the file`() =
        runTest {
            repository().observe(FILE_ID).test {
                awaitItem()

                client.emit(updateFile(downloading(downloadedSize = 512)))
                client.emit(updateFile(downloaded()))

                assertEquals(FileState.Downloading(downloadedSize = 512, size = SIZE, isActive = true), awaitItem())
                assertEquals(FileState.Downloaded(path = PATH, size = SIZE), awaitItem())
            }
        }

    @Test
    fun `observe ignores updates of other files`() =
        runTest {
            repository().observe(FILE_ID).test {
                awaitItem()

                client.emit(updateFile(file(id = OTHER_FILE_ID, size = SIZE, isDownloadingActive = true)))

                expectNoEvents()
            }
        }

    @Test
    fun `an update while TDLib has not answered yet is not missed`() =
        runTest {
            val never = CompletableDeferred<TdResult<TdApi.File>>()
            client.on(TdApi.GetFile::class) { never.await() }

            repository().observe(FILE_ID).test {
                client.emit(updateFile(downloaded()))

                assertEquals(FileState.Downloaded(path = PATH, size = SIZE), awaitItem())
            }
        }

    @Test
    fun `an update that came before the answer wins over it`() =
        runTest {
            client.on(TdApi.GetFile::class) {
                // The answer was read before the download completed, but arrives after the update about it.
                client.emit(updateFile(downloaded()))
                TdResult.Success(downloading(downloadedSize = 512))
            }

            assertEquals(FileState.Downloaded(path = PATH, size = SIZE), repository().observe(FILE_ID).first())
        }

    @Test
    fun `a second observer gets the cached state without asking TDLib again`() =
        runTest {
            val repository = repository()

            repository.observe(FILE_ID).test {
                awaitItem()

                repository.observe(FILE_ID).first()

                assertEquals(1, client.sentOf<TdApi.GetFile>().size)
            }
        }

    @Test
    fun `updates of files nobody observes are not cached`() =
        runTest {
            val repository = repository()
            client.emit(updateFile(downloaded()))

            val state = repository.observe(FILE_ID).first()

            assertEquals(FileState.NotDownloaded(size = SIZE, canBeDownloaded = true), state)
        }

    @Test
    fun `a file nobody observes any more is forgotten`() =
        runTest {
            val repository = repository()
            repository.observe(FILE_ID).first()

            repository.observe(FILE_ID).first()

            assertEquals(2, client.sentOf<TdApi.GetFile>().size)
        }

    @Test
    fun `observe emits nothing for a file TDLib does not know`() =
        runTest {
            client.on(TdApi.GetFile::class, TdResult.Failure(TdError(404, "Not Found")))

            repository().observe(FILE_ID).test {
                expectNoEvents()
            }
        }

    @Test
    fun `forgets observed files when TDLib closes`() =
        runTest {
            val repository = repository()

            repository.observe(FILE_ID).test {
                awaitItem()

                client.emitAuthorizationState(TdApiFixtures.closed())
                repository.observe(FILE_ID).first()

                assertEquals(2, client.sentOf<TdApi.GetFile>().size)
            }
        }

    @Test
    fun `download asks TDLib to download the whole file in the background`() =
        runTest {
            client.on(TdApi.DownloadFile::class) { request -> TdResult.Success(file(id = request.fileId)) }

            val result = repository().download(FILE_ID, DownloadPriority.Visible)

            val sent = client.sentOf<TdApi.DownloadFile>().single()
            assertEquals(Outcome.Success(Unit), result)
            assertEquals(
                listOf<Any>(FILE_ID, 32, 0L, 0L, false),
                listOf(sent.fileId, sent.priority, sent.offset, sent.limit, sent.synchronous),
            )
        }

    @Test
    fun `observeUpload follows how far a file sent from this device has uploaded`() =
        runTest {
            client.on(TdApi.GetFile::class) { request ->
                TdResult.Success(uploading(request.fileId, uploadedSize = 0, isActive = false))
            }

            repository().observeUpload(FILE_ID).test {
                val waiting = awaitItem()
                assertEquals(FileUpload(uploadedSize = 0, size = SIZE, isActive = false, isCompleted = false), waiting)

                client.emit(updateFile(uploading(FILE_ID, uploadedSize = 512, isActive = true)))
                // The file as TDLib reports it once uploaded: complete on both sides.
                client.emit(updateFile(downloaded()))

                val halfway = awaitItem()
                assertEquals(FileUpload(uploadedSize = 512, size = SIZE, isActive = true, isCompleted = false), halfway)
                assertEquals(0.25f, halfway.progress)
                assertEquals(1f, awaitItem().progress)
            }
        }

    @Test
    fun `a file observed for its state and its upload is asked for once`() =
        runTest {
            val repository = repository()

            repository.observe(FILE_ID).test {
                awaitItem()
                repository.observeUpload(FILE_ID).first()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, client.sentOf<TdApi.GetFile>().size)
        }

    @Test
    fun `download maps failures`() =
        runTest {
            client.on(TdApi.DownloadFile::class, TdResult.Failure(TdError(400, "File not found")))

            assertEquals(
                Outcome.Failure(DomainError.NotFound),
                repository().download(FILE_ID, DownloadPriority.Background),
            )
        }

    @Test
    fun `cancelDownload stops the download, whether it started or not`() =
        runTest {
            client.on(TdApi.CancelDownloadFile::class, TdResult.Success(TdApi.Ok()))

            val result = repository().cancelDownload(FILE_ID)

            val sent = client.sentOf<TdApi.CancelDownloadFile>().single()
            assertEquals(Outcome.Success(Unit), result)
            assertEquals(listOf<Any>(FILE_ID, false), listOf(sent.fileId, sent.onlyIfPending))
        }

    private companion object {
        const val FILE_ID = 7
        const val OTHER_FILE_ID = 8
        const val SIZE = 2_048L
        const val PATH = "/files/photos/7.jpg"
    }
}
