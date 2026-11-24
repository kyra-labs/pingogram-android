package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.common.result.onSuccess
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.tdlibPriority
import dev.kyralabs.pingogram.core.data.mapper.toFileState
import dev.kyralabs.pingogram.core.data.mapper.toFileUpload
import dev.kyralabs.pingogram.core.data.updates.updateRouter
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FileUpload
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * [FileRepository] on TDLib's file API. Only files being observed are followed: [observe] and [observeUpload] add a
 * file for as long as they are collected (counting collectors), and `updateFile` for other files is ignored. Each
 * observed file has a state of its own, so an update wakes only that file's observers (`docs/PERFORMANCE.md` §3).
 * Observing a file again asks TDLib, which answers from memory, unless the file was last seen downloaded in full: up
 * to [MAX_COMPLETED_FILES] of those are remembered, and show at once, until an update says otherwise. Downloads the
 * user asked for ([DownloadPriority.Requested]) go through [TdlibTransferRepository], which follows them as transfers.
 */
@AccountScoped
class TdlibFileRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        private val transfers: TdlibTransferRepository,
        @ApplicationScope scope: CoroutineScope,
    ) : FileRepository,
        TdlibUpdateCache {
        private val files = ObservedFiles()

        init {
            // Registered when this returns, so no update emitted later is missed.
            client.updateRouter(scope).onUpdates(FILE_UPDATES) { update ->
                when {
                    update is TdApi.UpdateFile -> files.report(update.file)
                    update.closesTdlib() -> files.clear()
                }
            }
        }

        override fun observe(fileId: Int): Flow<FileState> = observed(fileId) { it.state }

        override fun observeUpload(fileId: Int): Flow<FileUpload> = observed(fileId) { it.upload }

        /** What [select] reads of file [fileId] while it is observed, then every change of it. */
        private fun <T : Any> observed(
            fileId: Int,
            select: (ReportedFile) -> T,
        ): Flow<T> =
            flow {
                // Observed before asking TDLib, so no updateFile in between is ignored.
                val slot = files.acquire(fileId)
                try {
                    coroutineScope {
                        // Alongside, so updates show even before TDLib answered.
                        if (slot.reported.value == null) launch { fetch(slot) }
                        emitAll(slot.reported.mapNotNull { reported -> reported?.let(select) }.distinctUntilChanged())
                    }
                } finally {
                    files.release(slot)
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

        private suspend fun fetch(slot: FileSlot) {
            client.request(TdApi.GetFile(slot.fileId)).onSuccess { file -> files.reportIfUnknown(slot, file) }
        }
    }

/** Completely downloaded files remembered once nobody observes them, so observing one again asks TDLib nothing. */
internal const val MAX_COMPLETED_FILES = 1_000

/** What TDLib last reported of a file: its download [state] and its [upload]. */
private data class ReportedFile(
    val state: FileState,
    val upload: FileUpload,
)

private fun TdApi.File.reported() = ReportedFile(toFileState(), toFileUpload())

/** File [fileId] while someone observes it: how many collectors, and what TDLib [reported] of it, null until then. */
private class FileSlot(
    val fileId: Int,
    initial: ReportedFile?,
) {
    var collectors = 0
    val reported = MutableStateFlow(initial)
}

/**
 * The observed files, each with its own [FileSlot], and the [MAX_COMPLETED_FILES] most recently seen completely
 * downloaded, which a new slot starts from. Called from the update router and from collectors: every function holds
 * the lock.
 */
private class ObservedFiles {
    private val slots = HashMap<Int, FileSlot>()
    private val completed =
        object : LinkedHashMap<Int, ReportedFile>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ReportedFile>) =
                size > MAX_COMPLETED_FILES
        }

    @Synchronized
    fun acquire(fileId: Int): FileSlot =
        slots.getOrPut(fileId) { FileSlot(fileId, completed[fileId]) }.also { it.collectors++ }

    @Synchronized
    fun release(slot: FileSlot) {
        slot.collectors--
        if (slot.collectors <= 0 && slots[slot.fileId] === slot) slots.remove(slot.fileId)
    }

    /** An `updateFile`: for the file's observers, and for the completed files, which forget one no longer complete. */
    @Synchronized
    fun report(file: TdApi.File) {
        val slot = slots[file.id]
        if (slot == null && file.id !in completed) return
        val reported = file.reported()
        slot?.reported?.value = reported
        remember(file.id, reported)
    }

    /**
     * TDLib's answer to `getFile`, unless an `updateFile` came first: TDLib reports every change of a file, so the
     * updates alone end at the answer's state or a newer one, while the answer, which takes another path, can be older
     * than an update already applied.
     */
    @Synchronized
    fun reportIfUnknown(
        slot: FileSlot,
        file: TdApi.File,
    ) {
        if (slot.reported.value != null) return
        val reported = file.reported()
        slot.reported.value = reported
        remember(file.id, reported)
    }

    /** TDLib closed: its file ids mean nothing to the next instance. */
    @Synchronized
    fun clear() {
        slots.values.forEach { it.reported.value = null }
        completed.clear()
    }

    private fun remember(
        fileId: Int,
        reported: ReportedFile,
    ) {
        if (reported.state is FileState.Downloaded) completed[fileId] = reported else completed.remove(fileId)
    }

    private companion object {
        const val INITIAL_CAPACITY = 64
        const val LOAD_FACTOR = 0.75f
    }
}

/** The updates the repository handles; the closing one resets it. */
private val FILE_UPDATES: Set<KClass<out TdApi.Update>> =
    setOf(TdApi.UpdateFile::class, TdApi.UpdateAuthorizationState::class)
