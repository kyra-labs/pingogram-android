package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.kyralabs.pingogram.core.domain.model.FileState
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap

// A download's progress, kept out of the viewer's state: TDLib reports it many times a second, and every new state
// composed the whole screen again (the bars, the toolbar and the page) while a photo downloaded, also during the
// container transform (docs/audit/performance-review-2026-11.md, M4). The state keeps each file's kind of state
// (withoutProgress); the indicators read the progress itself through LocalFileProgress, where they draw it.

/**
 * The download progress of a file by its id, from 0 to 1, or null where the screen has none (previews and tests of the
 * screen alone, which put it in the state's file states instead). Read by the indicators only.
 */
internal val LocalFileProgress = staticCompositionLocalOf<(fileId: Int) -> Float?> { { null } }

/** The current item's full file, by its [id], in [state]: what an action that waits for it shows the progress of. */
@Immutable
internal data class ItemFile(
    val id: Int,
    val state: FileState?,
)

/** This state without its progress: a download is a download, however far it is. */
internal fun FileState.withoutProgress(): FileState =
    if (this is FileState.Downloading) copy(downloadedSize = 0) else this

/** [states] without their progress ([withoutProgress]). */
internal fun withoutProgress(states: ImmutableMap<Int, FileState>): ImmutableMap<Int, FileState> =
    states.mapValues { it.value.withoutProgress() }.toImmutableMap()

/** The progress of the files of [states] that are downloading. */
internal fun progressOf(states: Map<Int, FileState>): ImmutableMap<Int, Float> =
    states
        .mapNotNull { (id, state) -> (state as? FileState.Downloading)?.let { id to it.progress } }
        .toMap()
        .toImmutableMap()

/** The progress of [fileId] where the screen has it ([LocalFileProgress]), else what its [state] says. */
internal fun progressOf(
    fileId: Int,
    state: FileState?,
    lookup: (Int) -> Float?,
): Float =
    when (state) {
        is FileState.Downloading -> lookup(fileId) ?: state.progress
        is FileState.Downloaded -> 1f
        is FileState.NotDownloaded, null -> 0f
    }
