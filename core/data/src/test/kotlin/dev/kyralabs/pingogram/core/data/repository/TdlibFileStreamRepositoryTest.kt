package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.FileRange
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateFile
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Streaming a TDLib file while it downloads (`docs/adr/0028-streaming-media.md`). */
class TdlibFileStreamRepositoryTest {
    /** The file as TDLib has it now, which `getFile` answers. */
    private var current = partial(offset = 0, prefix = 0, active = false)

    private val client =
        FakeTdlibClient().apply {
            on(TdApi.GetFile::class) { request ->
                if (request.fileId == FILE_ID) {
                    TdResult.Success(current)
                } else {
                    TdResult.Failure(TdError(404, "Not Found"))
                }
            }
            on(TdApi.DownloadFile::class) { TdResult.Success(current) }
            on(TdApi.CancelDownloadFile::class) { TdResult.Success(TdApi.Ok()) }
        }
    private val repository = TdlibFileStreamRepository(client)

    @Test
    fun `a range already on the device answers at once, downloading nothing`() =
        runTest {
            current = partial(offset = 0, prefix = 4_096, active = false)

            val range = repository.awaitRange(FILE_ID, 1_000, 1_024)

            assertEquals(Outcome.Success(FileRange(PATH, 3_096, SIZE)), range)
            assertTrue(client.sentOf<TdApi.DownloadFile>().isEmpty())
        }

    @Test
    fun `a range not there yet downloads from its offset at visible priority, and answers once it came`() =
        runTest {
            val range = async { repository.awaitRange(FILE_ID, 10_000, 1_024) }
            runCurrent()

            val download = client.sentOf<TdApi.DownloadFile>().single()
            assertEquals(10_000L, download.offset)
            assertEquals(0L, download.limit)
            assertEquals(32, download.priority)
            assertTrue(!download.synchronous)

            client.emit(updateFile(partial(offset = 10_000, prefix = 512, active = true)))
            runCurrent()
            assertTrue(range.isActive)

            client.emit(updateFile(partial(offset = 10_000, prefix = 2_048, active = true)))
            assertEquals(Outcome.Success(FileRange(PATH, 2_048, SIZE)), range.await())
        }

    @Test
    fun `the end of the file answers with what is left, and a complete file answers at once`() =
        runTest {
            current = partial(offset = SIZE - 100, prefix = 100, active = true)
            assertEquals(
                Outcome.Success(FileRange(PATH, 100, SIZE)),
                repository.awaitRange(FILE_ID, SIZE - 100, 65_536),
            )

            current =
                TdApi.File(FILE_ID, SIZE, SIZE, TdApi.LocalFile(PATH, true, true, false, true, 0, SIZE, SIZE), remote())
            assertEquals(Outcome.Success(FileRange(PATH, SIZE - 5, SIZE)), repository.awaitRange(FILE_ID, 5, 65_536))
        }

    @Test
    fun `a download that stops short of the range fails, and an unknown file is not found`() =
        runTest {
            val range = async { repository.awaitRange(FILE_ID, 0, 1_024) }
            runCurrent()

            client.emit(updateFile(partial(offset = 0, prefix = 100, active = false)))

            assertEquals(Outcome.Failure(DomainError.Network), range.await())
            assertEquals(Outcome.Failure(DomainError.NotFound), repository.awaitRange(OTHER_FILE_ID, 0, 1))
        }

    @Test
    fun `reading on within the running download starts nothing new, a seek elsewhere moves it`() =
        runTest {
            val first = async { repository.awaitRange(FILE_ID, 0, 1_024) }
            runCurrent()
            current = partial(offset = 0, prefix = 1_024, active = true)
            client.emit(updateFile(current))
            first.await()

            val next = async { repository.awaitRange(FILE_ID, 1_024, 1_024) }
            runCurrent()
            assertEquals(1, client.sentOf<TdApi.DownloadFile>().size)
            current = partial(offset = 0, prefix = 2_048, active = true)
            client.emit(updateFile(current))
            next.await()

            val seek = async { repository.awaitRange(FILE_ID, 500_000, 1_024) }
            runCurrent()
            assertEquals(listOf(0L, 500_000L), client.sentOf<TdApi.DownloadFile>().map { it.offset })
            client.emit(updateFile(partial(offset = 500_000, prefix = 1_024, active = true)))
            seek.await()
        }

    @Test
    fun `stopping cancels the download a reader started, keeping what came, but not a complete file's`() =
        runTest {
            repository.stop(FILE_ID)
            assertTrue(client.sentOf<TdApi.CancelDownloadFile>().isEmpty())

            val range = async { repository.awaitRange(FILE_ID, 0, 1_024) }
            runCurrent()
            current = partial(offset = 0, prefix = 1_024, active = true)
            client.emit(updateFile(current))
            range.await()

            repository.stop(FILE_ID)

            val cancel = client.sentOf<TdApi.CancelDownloadFile>().single()
            assertEquals(FILE_ID, cancel.fileId)
            assertTrue(!cancel.onlyIfPending)
        }

    private fun partial(
        offset: Long,
        prefix: Long,
        active: Boolean,
    ) = TdApi.File(
        FILE_ID,
        SIZE,
        SIZE,
        TdApi.LocalFile(PATH, true, true, active, false, offset, prefix, prefix),
        remote(),
    )

    private fun remote() = TdApi.RemoteFile("remote", "unique", false, true, SIZE)

    private companion object {
        const val FILE_ID = 3
        const val OTHER_FILE_ID = 4
        const val SIZE = 1_000_000L
        const val PATH = "/files/videos/clip.mp4"
    }
}
