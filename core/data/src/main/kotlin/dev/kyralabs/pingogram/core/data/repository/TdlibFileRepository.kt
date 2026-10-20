package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.common.result.onSuccess
import dev.kyralabs.pingogram.core.data.mapper.tdlibPriority
import dev.kyralabs.pingogram.core.data.mapper.toFileState
import dev.kyralabs.pingogram.core.data.mapper.toFileUpload
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FileUpload
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [FileRepository] on TDLib's file API. Only files being observed are cached: [observe] and [observeUpload] add a file
 * for as long as they are collected (counting collectors), so the cache holds what is on screen, and `updateFile` for
 * other files is ignored. Observing a file again asks TDLib, which answers from memory. Downloads the user asked for
 * ([DownloadPriority.Requested]) go through [TdlibTransferRepository], which follows them as transfers.
 */
@Singleton
class TdlibFileRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        private val transfers: TdlibTransferRepository,
        @ApplicationScope scope: CoroutineScope,
    ) : FileRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, persistentMapOf(), ObservedFiles::reduce)

        override fun observe(fileId: Int): Flow<FileState> = observed(fileId) { it.state }

        override fun observeUpload(fileId: Int): Flow<FileUpload> = observed(fileId) { it.upload }

        /** What [select] reads of file [fileId] while it is observed, then every change of it. */
        private fun <T : Any> observed(
            fileId: Int,
            select: (ObservedFile) -> T?,
        ): Flow<T> =
            flow {
                // Observed before asking TDLib, so no updateFile in between is ignored.
                store.update { files -> files.observe(fileId) }
                try {
                    coroutineScope {
                        // Alongside, so updates show even before TDLib answered.
                        if (store.state.value[fileId]?.state == null) launch { fetch(fileId) }
                        emitAll(
                            store.state.mapNotNull { files -> files[fileId]?.let(select) }.distinctUntilChanged(),
                        )
                    }
                } finally {
                    store.update { files -> files.release(fileId) }
                }
            }

        override suspend fun download(
            fileId: Int,
            priority: DownloadPriority,
        ): Outcome<Unit> {
            // The user's own downloads are transfers that keep the app running in the background (FR-N4).
            if (priority == DownloadPriority.Requested) return transfers.download(fileId, priority.tdlibPriority)
            // The whole file (offset 0, no limit), answered at once rather than when the download completes.
            val function = TdApi.DownloadFile(fileId, priority.tdlibPriority, 0, 0, false)
            return client.request(function).map {}
        }

        override suspend fun cancelDownload(fileId: Int): Outcome<Unit> =
            client.request(TdApi.CancelDownloadFile(fileId, false)).map {}

        private suspend fun fetch(fileId: Int) {
            client.request(TdApi.GetFile(fileId)).onSuccess { file ->
                store.update { files -> files.putIfUnknown(fileId, file) }
            }
        }
    }

/** A file someone observes: how many collectors, and its state and upload once TDLib reported it. */
private data class ObservedFile(
    val collectors: Int,
    val state: FileState?,
    val upload: FileUpload? = null,
) {
    fun reported(file: TdApi.File) = copy(state = file.toFileState(), upload = file.toFileUpload())
}

private typealias ObservedFiles = PersistentMap<Int, ObservedFile>

private fun ObservedFiles.reduce(update: TdApi.Update): ObservedFiles {
    if (update !is TdApi.UpdateFile) return this
    val file = update.file
    return get(file.id)?.let { observed -> putting(file.id, observed.reported(file)) } ?: this
}

private fun ObservedFiles.observe(fileId: Int): ObservedFiles {
    val observed = get(fileId)
    return putting(fileId, observed?.copy(collectors = observed.collectors + 1) ?: ObservedFile(1, state = null))
}

private fun ObservedFiles.release(fileId: Int): ObservedFiles {
    // Gone already if TDLib closed in between.
    val observed = get(fileId) ?: return this
    return when {
        observed.collectors <= 1 -> removing(fileId)
        else -> putting(fileId, observed.copy(collectors = observed.collectors - 1))
    }
}

/**
 * Stores a requested state unless an `updateFile` came first. TDLib reports every change of a file, so the updates
 * alone end at the answer's state or a newer one, while the answer, which takes another path, can be older than an
 * update already applied.
 */
private fun ObservedFiles.putIfUnknown(
    fileId: Int,
    file: TdApi.File,
): ObservedFiles {
    val observed = get(fileId)
    return if (observed != null && observed.state == null) putting(fileId, observed.reported(file)) else this
}
