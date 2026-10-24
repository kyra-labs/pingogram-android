package dev.kyralabs.pingogram.core.ui.media

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.FileRange
import dev.kyralabs.pingogram.core.domain.repository.FileStreamRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile

/**
 * A Media3 [DataSource] over a TDLib file that may still be downloading (`docs/adr/0028-streaming-media.md`): reads
 * wait on the player's loading thread until [FileStreamRepository] has the bytes, so a video or a song plays as it
 * arrives, and a seek downloads from where it lands. [close] stops a download the file did not finish, as the
 * player leaving the file closes it ([FileStreamRepository.stop]).
 *
 * Its URIs name the file by its TDLib id ([uriOf]); a read waits at most [READ_TIMEOUT_MILLIS] for its bytes, then
 * fails as a network error, which the player reports.
 */
@OptIn(UnstableApi::class)
class TdlibDataSource(
    private val files: FileStreamRepository,
) : BaseDataSource(true) {
    private var uri: Uri? = null
    private var fileId = 0
    private var position = 0L
    private var remaining = 0L
    private var available = 0L
    private var path: String? = null
    private var file: RandomAccessFile? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        fileId = fileIdOf(dataSpec.uri) ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        uri = dataSpec.uri
        position = dataSpec.position
        val range = awaitRange(minimum = 1)
        remaining =
            if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else if (range.size > 0) {
                range.size - position
            } else {
                C.LENGTH_UNSET.toLong()
            }
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int =
        when {
            length == 0 -> 0
            remaining == 0L -> C.RESULT_END_OF_INPUT
            else -> readAvailable(buffer, offset, length)
        }

    /** Reads up to [length] bytes of what is on the device from [position], waiting for some first if none is. */
    private fun readAvailable(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (available <= 0) awaitRange(minimum = MIN_READ_BYTES)
        val wanted = minOf(length.toLong(), available, if (remaining > 0) remaining else Long.MAX_VALUE).toInt()
        val read =
            openFile().run {
                seek(position)
                read(buffer, offset, wanted)
            }
        if (read < 0) return C.RESULT_END_OF_INPUT
        position += read
        available -= read
        if (remaining > 0) remaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        val opened = uri != null
        uri = null
        path = null
        file?.close()
        file = null
        if (opened) {
            runBlocking { files.stop(fileId) }
            transferEnded()
        }
    }

    /** Waits for at least [minimum] bytes from [position] (fewer at the end) and notes where they are. */
    private fun awaitRange(minimum: Long): FileRange {
        val outcome =
            try {
                runBlocking { withTimeoutOrNull(READ_TIMEOUT_MILLIS) { files.awaitRange(fileId, position, minimum) } }
            } catch (e: InterruptedException) {
                // The player cancelled the load (a seek, a release): it interrupts its loading thread.
                Thread.currentThread().interrupt()
                throw InterruptedIOException().apply { initCause(e) }
            }
        val range =
            (outcome as? Outcome.Success)?.value
                ?: throw DataSourceException(
                    IOException("File not available"),
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                )
        if (range.path != path) {
            file?.close()
            file = null
            path = range.path
        }
        available = range.available
        return range
    }

    private fun openFile(): RandomAccessFile = file ?: RandomAccessFile(path, "r").also { file = it }

    /** Makes [TdlibDataSource]s for a player. */
    class Factory(
        private val files: FileStreamRepository,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = TdlibDataSource(files)
    }

    companion object {
        private const val SCHEME = "tdlib-file"

        /** At least this much at a time, so a slow download is not read a few bytes at a time. */
        private const val MIN_READ_BYTES = 64L * 1024

        const val READ_TIMEOUT_MILLIS = 20_000L

        /** The URI a player plays TDLib file [fileId] by. */
        fun uriOf(fileId: Int): Uri =
            Uri
                .Builder()
                .scheme(SCHEME)
                .authority(fileId.toString())
                .build()

        private fun fileIdOf(uri: Uri): Int? = uri.authority?.toIntOrNull()?.takeIf { uri.scheme == SCHEME }
    }
}
