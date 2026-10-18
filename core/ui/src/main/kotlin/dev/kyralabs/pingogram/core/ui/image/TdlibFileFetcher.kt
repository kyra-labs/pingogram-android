package dev.kyralabs.pingogram.core.ui.image

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.IOException

/**
 * Loads a [TdlibFileImage] from TDLib's files (`docs/ARCHITECTURE.md`, Files and media). It follows the file through
 * [FileRepository.observe] and, if the file is not on the device yet, asks TDLib once to download it at
 * [DownloadPriority.Visible]. As soon as the file is downloaded it hands Coil the local file, read in place: TDLib
 * keeps the file, so Coil needs no disk cache of its own (`docs/PERFORMANCE.md` §4).
 *
 * The request fails with a [TdlibFileImageException] when TDLib cannot download the file, refuses the download, or
 * the download stops before completing (it failed, or something cancelled it); it is not retried.
 *
 * Cancelling the request, e.g. when the image leaves the screen, stops waiting but lets TDLib's download finish:
 * avatars and thumbnails are about 10 KB, so finishing costs less than cancelling and starting over, and the file is
 * ready the next time it is shown. A file id TDLib does not know is waited for until the request is cancelled.
 */
class TdlibFileFetcher internal constructor(
    private val image: TdlibFileImage,
    private val fileRepository: FileRepository,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val download = Download()
        val path = fileRepository.observe(image.fileId).mapNotNull(download::pathOnceDownloaded).first()
        return SourceFetchResult(
            source = ImageSource(file = path.toPath(), fileSystem = FileSystem.SYSTEM),
            // Coil's decoders recognise the format from the file's header.
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    /** The download of one request: started at most once, and failed once it stopped. */
    private inner class Download {
        private var requested = false
        private var started = false

        /** The local path once the file is downloaded; null while waiting for it. */
        suspend fun pathOnceDownloaded(state: FileState): String? {
            when (state) {
                is FileState.Downloaded -> return state.path.ifEmpty { fail("downloaded without a path") }
                is FileState.NotDownloaded -> if (!state.canBeDownloaded) fail("cannot be downloaded")
                is FileState.Downloading -> if (state.isActive) started = true
            }
            // Only an inactive state that follows an active one: right after the request, TDLib may still report the
            // state from before it.
            val active = state is FileState.Downloading && state.isActive
            if (started && !active) fail("the download stopped")
            if (!requested) {
                requested = true
                fileRepository
                    .download(image.fileId, DownloadPriority.Visible)
                    .onFailure { error -> fail("the download was refused ($error)") }
            }
            return null
        }

        private fun fail(reason: String): Nothing = throw TdlibFileImageException(image.fileId, reason)
    }

    /** Creates a [TdlibFileFetcher] for each [TdlibFileImage]; register it in the app's `ImageLoader`. */
    class Factory(
        private val fileRepository: FileRepository,
    ) : Fetcher.Factory<TdlibFileImage> {
        override fun create(
            data: TdlibFileImage,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = TdlibFileFetcher(data, fileRepository)
    }
}

/** Why TDLib file [fileId] could not be shown; Coil reports it as the request's error. */
class TdlibFileImageException(
    val fileId: Int,
    reason: String,
) : IOException("TDLib file $fileId: $reason")
