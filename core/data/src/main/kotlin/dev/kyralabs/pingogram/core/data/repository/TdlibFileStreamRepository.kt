package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.repository.FileRange
import dev.kyralabs.pingogram.core.domain.repository.FileStreamRepository
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * [FileStreamRepository] on TDLib's partial downloads: `downloadFile` from an offset (`limit` 0: to the end), then
 * the file's `updateFile`s until enough of it is there, read from its `local` part (`download_offset`,
 * `downloaded_prefix_size`). No cache: each wait subscribes to the updates before it asks, and ends with the range.
 * A reader seeking within what downloads already starts nothing new; one seeking elsewhere moves the download there.
 */
@AccountScoped
class TdlibFileStreamRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : FileStreamRepository {
        /** The offset each streamed file downloads from, for the files a reader started. */
        private val downloads = ConcurrentHashMap<Int, Long>()

        override suspend fun awaitRange(
            fileId: Int,
            offset: Long,
            length: Long,
        ): Outcome<FileRange> =
            coroutineScope {
                val requested = AtomicBoolean(false)
                // Subscribed before anything is asked, so no update in between is missed. A download that stopped
                // short of the range (cancelled, failed) ends the wait, once this reader's request is in.
                val reached =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        client.updates
                            .filterIsInstance<TdApi.UpdateFile>()
                            .map { it.file }
                            .first { file ->
                                file.id == fileId &&
                                    (file.range(offset, length) != null || (requested.get() && !file.isDownloading()))
                            }
                    }
                val outcome =
                    when (val file = fetch(fileId)) {
                        null -> {
                            Outcome.Failure(DomainError.NotFound)
                        }

                        else -> {
                            file.range(offset, length)?.let { Outcome.Success(it) }
                                ?: if (download(file, offset)) {
                                    requested.set(true)
                                    null
                                } else {
                                    Outcome.Failure(DomainError.Network)
                                }
                        }
                    }
                if (outcome != null) {
                    reached.cancel()
                    return@coroutineScope outcome
                }
                reached.await().range(offset, length)?.let { Outcome.Success(it) }
                    ?: Outcome.Failure(DomainError.Network)
            }

        override suspend fun stop(fileId: Int) {
            downloads.remove(fileId) ?: return
            val file = fetch(fileId) ?: return
            if (!file.local.isDownloadingCompleted) client.send(TdApi.CancelDownloadFile(fileId, false))
        }

        private suspend fun fetch(fileId: Int): TdApi.File? =
            (client.send(TdApi.GetFile(fileId)) as? TdResult.Success)?.value

        /**
         * Downloads [file] from [offset] unless a download running for this reader already covers it; false if TDLib
         * refused.
         */
        private suspend fun download(
            file: TdApi.File,
            offset: Long,
        ): Boolean {
            val local = file.local
            val from = downloads[file.id]
            val covered =
                local.isDownloadingActive &&
                    from != null &&
                    offset >= local.downloadOffset &&
                    offset <= local.downloadOffset + local.downloadedPrefixSize
            if (covered) return true
            downloads[file.id] = offset
            // limit 0 downloads to the end; asynchronous, so the updates report it.
            return client.send(TdApi.DownloadFile(file.id, STREAM_PRIORITY, offset, 0, false)) is TdResult.Success
        }

        private companion object {
            /** As visible media (`docs/PERFORMANCE.md` §3): what plays is on screen. */
            const val STREAM_PRIORITY = 32
        }
    }

/** The range of [length] bytes from [offset] once it is on the device, or null while it is not. */
private fun TdApi.File.range(
    offset: Long,
    length: Long,
): FileRange? {
    val local = local?.takeUnless { it.path.isNullOrEmpty() } ?: return null
    val total = if (size > 0) size else expectedSize
    val end = local.downloadOffset + local.downloadedPrefixSize
    val needed = if (total > 0) minOf(length, total - offset) else length
    return when {
        local.isDownloadingCompleted -> FileRange(local.path, (total - offset).coerceAtLeast(0), total)
        offset in local.downloadOffset..end && end - offset >= needed -> FileRange(local.path, end - offset, total)
        else -> null
    }
}

private fun TdApi.File.isDownloading() = local?.isDownloadingActive == true
