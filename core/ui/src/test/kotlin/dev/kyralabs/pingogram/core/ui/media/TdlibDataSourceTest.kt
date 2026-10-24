package dev.kyralabs.pingogram.core.ui.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.FileRange
import dev.kyralabs.pingogram.core.domain.repository.FileStreamRepository
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [TdlibDataSource] reading a file as it downloads (`docs/adr/0028-streaming-media.md`). */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class TdlibDataSourceTest {
    @get:Rule val folder = TemporaryFolder()

    private val bytes = ByteArray(SIZE) { it.toByte() }
    private val files = FakeFileStreamRepository()

    private fun source(downloaded: Int): TdlibDataSource {
        val file = folder.newFile("clip.mp4").apply { writeBytes(bytes) }
        files.path = file.path
        files.downloaded = downloaded
        return TdlibDataSource(files)
    }

    @Test
    fun `opens at the asked position with the file's length, and reads what is there`() {
        val source = source(downloaded = SIZE)

        val length =
            source.open(
                DataSpec
                    .Builder()
                    .setUri(TdlibDataSource.uriOf(FILE_ID))
                    .setPosition(10)
                    .build(),
            )
        val buffer = ByteArray(5)
        val read = source.read(buffer, 0, 5)

        assertEquals(SIZE - 10L, length)
        assertEquals(5, read)
        assertArrayEquals(bytes.copyOfRange(10, 15), buffer)
        assertEquals(listOf(FILE_ID to 10L), files.asked.take(1))
    }

    @Test
    fun `reads no further than downloaded, then waits for more from where it is`() {
        val source = source(downloaded = 20)
        source.open(DataSpec(TdlibDataSource.uriOf(FILE_ID)))

        val buffer = ByteArray(SIZE)
        assertEquals(20, source.read(buffer, 0, SIZE))
        files.downloaded = SIZE
        assertEquals(SIZE - 20, source.read(buffer, 20, SIZE))

        assertArrayEquals(bytes, buffer)
        assertEquals(20L, files.asked.last().second)
        assertEquals(C.RESULT_END_OF_INPUT, source.read(buffer, 0, 1))
    }

    @Test
    fun `closing stops the download, and a file TDLib cannot give fails as a network error`() {
        val source = source(downloaded = SIZE)
        source.open(DataSpec(TdlibDataSource.uriOf(FILE_ID)))
        source.close()
        assertEquals(listOf(FILE_ID), files.stopped)

        files.fails = true
        val failing = TdlibDataSource(files)
        assertThrows(DataSourceException::class.java) { failing.open(DataSpec(TdlibDataSource.uriOf(FILE_ID))) }
    }

    private class FakeFileStreamRepository : FileStreamRepository {
        var path = ""
        var downloaded = 0
        var fails = false
        val asked = mutableListOf<Pair<Int, Long>>()
        val stopped = mutableListOf<Int>()

        override suspend fun awaitRange(
            fileId: Int,
            offset: Long,
            length: Long,
        ): Outcome<FileRange> {
            asked += fileId to offset
            if (fails) return Outcome.Failure(DomainError.Network)
            return Outcome.Success(FileRange(path, downloaded - offset, SIZE.toLong()))
        }

        override suspend fun stop(fileId: Int) {
            stopped += fileId
        }
    }

    private companion object {
        const val FILE_ID = 12
        const val SIZE = 100
    }
}
