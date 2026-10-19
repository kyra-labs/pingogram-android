package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FileUpload
import kotlinx.coroutines.flow.Flow

/**
 * Downloads of TDLib files and their progress (`docs/ARCHITECTURE.md`, Files and media), and the progress of the files
 * this device sends, by the ids in `FileRef`.
 * File ids belong to one TDLib instance; after logout they mean nothing.
 */
interface FileRepository {
    /** The state of file [fileId], then every change. Emits nothing for an id TDLib does not know. */
    fun observe(fileId: Int): Flow<FileState>

    /**
     * How far file [fileId], sent from this device, has uploaded, then every change: the progress of a photo, video or
     * document while its message is being sent. Emits nothing for an id TDLib does not know.
     */
    fun observeUpload(fileId: Int): Flow<FileUpload>

    /**
     * Starts downloading [fileId] in the background, continues a stopped download, or changes the [priority] of a
     * running one; [observe] reports the progress. Succeeds once TDLib accepted the request.
     */
    suspend fun download(
        fileId: Int,
        priority: DownloadPriority,
    ): Outcome<Unit>

    /** Stops downloading [fileId]. What was downloaded stays, and [download] continues from there. */
    suspend fun cancelDownload(fileId: Int): Outcome<Unit>
}
