package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.runtime.Immutable

/** What is happening to a photo being edited ([PhotoEditor]): nothing, or one change at a time. */
@Immutable
sealed interface PhotoTask {
    data object None : PhotoTask

    /** The picked image is being copied and scaled down. */
    data object Preparing : PhotoTask

    /** The new photo is uploading: [progress] from 0 to 1, null until TDLib reports some. */
    data class Uploading(
        val progress: Float?,
    ) : PhotoTask

    data object Removing : PhotoTask
}
