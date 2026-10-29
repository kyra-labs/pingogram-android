package dev.kyralabs.pingogram.feature.media.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The downloads of the viewer's full files (`docs/PERFORMANCE.md` §3), for [MediaViewerViewModel], through
 * [FileRepository] in [scope]:
 *
 * - the shown item's at [DownloadPriority.Visible], its neighbours' at [DownloadPriority.Prefetch], so turning the
 *   page shows the next one at once; a neighbour that becomes the shown item moves up, and back down when it is a
 *   neighbour again;
 * - downloads it asked for of items more than [KEEP_DISTANCE] pages away are cancelled (what was downloaded stays,
 *   and the download continues from there), and so are the unfinished ones when the viewer closes ([cancelAll]);
 * - a file is asked for once per priority, once its state is known, and not at all once it is on the device;
 * - a video that streams as it plays ([focus]'s `streamed`) is left to its stream, which downloads from where it plays
 *   (`docs/adr/0028-streaming-media.md`): two downloads of one file would move each other's offset.
 *
 * A refused download of the shown item goes to [onError]; a neighbour's would only repeat it. Main thread only.
 */
internal class MediaDownloads(
    private val files: FileRepository,
    private val scope: CoroutineScope,
    private val onError: (DomainError) -> Unit,
) {
    /** The files asked for, by id, at the priority they were last asked for at. */
    private val requested = mutableMapOf<Int, DownloadPriority>()

    /**
     * Downloads around the item at [currentIndex] of [fileIds] (each item's full file, in pager order), whose known
     * states are [states], and cancels what is out of reach; the shown item's file [streamed] (if not null) is its
     * stream's to download.
     */
    fun focus(
        fileIds: List<Int>,
        currentIndex: Int,
        states: Map<Int, FileState>,
        streamed: Int? = null,
    ) {
        // Neither asked for again nor cancelled: the stream downloads it now.
        if (streamed != null) requested.remove(streamed)
        val wanted =
            buildMap {
                fileIds.getOrNull(currentIndex)?.let { put(it, DownloadPriority.Visible) }
                fileIds.getOrNull(currentIndex - 1)?.let { putIfAbsent(it, DownloadPriority.Prefetch) }
                fileIds.getOrNull(currentIndex + 1)?.let { putIfAbsent(it, DownloadPriority.Prefetch) }
            }
        for ((fileId, priority) in wanted) {
            if (fileId == streamed || requested[fileId] == priority || !states[fileId].needsDownload) continue
            requested[fileId] = priority
            scope.launch {
                files.download(fileId, priority).onFailure { if (priority == DownloadPriority.Visible) onError(it) }
            }
        }
        val inReach = (currentIndex - KEEP_DISTANCE..currentIndex + KEEP_DISTANCE).mapNotNull(fileIds::getOrNull)
        for (fileId in requested.keys - inReach.toSet()) cancel(fileId, scope)
    }

    /**
     * Cancels, in [scope], every download asked for that is not known to be complete by [states]: the viewer closes,
     * so its own scope is gone.
     */
    fun cancelAll(
        states: Map<Int, FileState>,
        scope: CoroutineScope,
    ) {
        for (fileId in requested.keys.toList()) {
            if (states[fileId] is FileState.Downloaded) requested.remove(fileId) else cancel(fileId, scope)
        }
    }

    private fun cancel(
        fileId: Int,
        scope: CoroutineScope,
    ) {
        requested.remove(fileId)
        scope.launch { files.cancelDownload(fileId) }
    }

    private companion object {
        /** How many pages away from the shown item downloads it asked for go on. */
        const val KEEP_DISTANCE = 2
    }
}

/**
 * The file is known to be missing, in part or whole, and TDLib can download it. Not while TDLib has not reported it
 * yet: it reports every followed file at once, and it may be on the device already.
 */
private val FileState?.needsDownload: Boolean
    get() =
        when (this) {
            is FileState.NotDownloaded -> canBeDownloaded
            is FileState.Downloading -> true
            is FileState.Downloaded, null -> false
        }
