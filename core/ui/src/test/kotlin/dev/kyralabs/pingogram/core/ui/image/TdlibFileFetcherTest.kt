package dev.kyralabs.pingogram.core.ui.image

import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FileUpload
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// runCurrent
@OptIn(ExperimentalCoroutinesApi::class)
class TdlibFileFetcherTest {
    private val files = FakeFileRepository()
    private val fetcher = TdlibFileFetcher(TdlibFileImage(FILE_ID), files)

    @Test
    fun `loads a downloaded file in place, from TDLib's files`() =
        runTest {
            files.state.value = FileState.Downloaded(PATH, SIZE)

            val result = fetcher.fetch()

            assertInstanceOf(SourceFetchResult::class.java, result)
            result as SourceFetchResult
            assertEquals(PATH.toPath(), result.source.file())
            assertEquals(DataSource.DISK, result.dataSource)
            assertEquals(listOf(FILE_ID), files.observed)
            assertEquals(emptyList<Int>(), files.downloads)
        }

    @Test
    fun `downloads a file that is not on the device yet, once, and loads it when complete`() =
        runTest {
            files.state.value = FileState.NotDownloaded(SIZE, canBeDownloaded = true)

            val result = async { fetcher.fetch() }
            runCurrent()
            files.state.value = FileState.Downloading(downloadedSize = SIZE / 2, size = SIZE, isActive = true)
            runCurrent()

            assertFalse(result.isCompleted)
            files.state.value = FileState.Downloaded(PATH, SIZE)
            assertEquals(PATH.toPath(), (result.await() as SourceFetchResult).source.file())
            assertEquals(listOf(FILE_ID to DownloadPriority.Visible), files.downloads)
        }

    @Test
    fun `continues a download that stopped before the request`() =
        runTest {
            files.state.value = FileState.Downloading(downloadedSize = SIZE / 2, size = SIZE, isActive = false)

            val result = async { fetcher.fetch() }
            runCurrent()
            files.state.value = FileState.Downloading(downloadedSize = SIZE / 2, size = SIZE, isActive = true)
            runCurrent()
            files.state.value = FileState.Downloaded(PATH, SIZE)

            assertEquals(PATH.toPath(), (result.await() as SourceFetchResult).source.file())
            assertEquals(listOf(FILE_ID to DownloadPriority.Visible), files.downloads)
        }

    @Test
    fun `fails without downloading when TDLib cannot download the file`() =
        runTest {
            files.state.value = FileState.NotDownloaded(SIZE, canBeDownloaded = false)

            val error = fetchError()

            assertEquals(FILE_ID, error.fileId)
            assertEquals(emptyList<Int>(), files.downloads)
        }

    @Test
    fun `fails when TDLib refuses the download`() =
        runTest {
            files.state.value = FileState.NotDownloaded(SIZE, canBeDownloaded = true)
            files.downloadResult = Outcome.Failure(DomainError.Network)

            assertEquals(FILE_ID, fetchError().fileId)
        }

    @Test
    fun `fails when the download stops before completing`() =
        runTest {
            files.state.value = FileState.NotDownloaded(SIZE, canBeDownloaded = true)

            val result = async { runCatching { fetcher.fetch() } }
            runCurrent()
            files.state.value = FileState.Downloading(downloadedSize = 0, size = SIZE, isActive = true)
            runCurrent()
            files.state.value = FileState.NotDownloaded(SIZE, canBeDownloaded = true)

            assertInstanceOf(TdlibFileImageException::class.java, result.await().exceptionOrNull())
        }

    @Test
    fun `cancelling stops waiting and leaves TDLib's download running`() =
        runTest {
            files.state.value = FileState.NotDownloaded(SIZE, canBeDownloaded = true)

            val request = launch { fetcher.fetch() }
            runCurrent()
            request.cancelAndJoin()

            assertTrue(request.isCancelled)
            assertEquals(0, files.observers)
            assertEquals(listOf(FILE_ID to DownloadPriority.Visible), files.downloads)
            assertEquals(emptyList<Int>(), files.cancelledDownloads)
        }

    @Test
    fun `without download, waits for a download started elsewhere, through a cancelled one, and asks for nothing`() =
        runTest {
            files.state.value = FileState.NotDownloaded(SIZE, canBeDownloaded = true)
            val waiting = TdlibFileFetcher(TdlibFileImage(FILE_ID, download = false), files)

            val result = async { waiting.fetch() }
            runCurrent()
            files.state.value = FileState.Downloading(downloadedSize = SIZE / 4, size = SIZE, isActive = true)
            runCurrent()
            files.state.value = FileState.Downloading(downloadedSize = SIZE / 4, size = SIZE, isActive = false)
            runCurrent()
            assertFalse(result.isCompleted)
            files.state.value = FileState.Downloaded(PATH, SIZE)

            assertEquals(PATH.toPath(), (result.await() as SourceFetchResult).source.file())
            assertEquals(emptyList<Pair<Int, DownloadPriority>>(), files.downloads)
        }

    private suspend fun fetchError(): TdlibFileImageException =
        assertInstanceOf(TdlibFileImageException::class.java, runCatching { fetcher.fetch() }.exceptionOrNull())

    private companion object {
        const val FILE_ID = 42
        const val SIZE = 10_000L
        const val PATH = "/data/user/0/dev.kyralabs.pingogram/files/tdlib/files/profile_photos/42.jpg"
    }
}

/** One file whose [state] tests set; null until TDLib "knows" it. */
private class FakeFileRepository : FileRepository {
    val state = MutableStateFlow<FileState?>(null)
    var downloadResult: Outcome<Unit> = Outcome.Success(Unit)
    val observed = mutableListOf<Int>()
    val downloads = mutableListOf<Pair<Int, DownloadPriority>>()
    val cancelledDownloads = mutableListOf<Int>()

    /** Collectors of [observe] right now. */
    var observers = 0
        private set

    // Images load downloaded files; uploads are not the fetcher's.
    override fun observeUpload(fileId: Int): Flow<FileUpload> = emptyFlow()

    override fun observe(fileId: Int): Flow<FileState> =
        flow {
            observed += fileId
            observers++
            try {
                emitAll(state.filterNotNull())
            } finally {
                observers--
            }
        }

    override suspend fun download(
        fileId: Int,
        priority: DownloadPriority,
    ): Outcome<Unit> {
        downloads += fileId to priority
        return downloadResult
    }

    override suspend fun cancelDownload(fileId: Int): Outcome<Unit> {
        cancelledDownloads += fileId
        return Outcome.Success(Unit)
    }
}
