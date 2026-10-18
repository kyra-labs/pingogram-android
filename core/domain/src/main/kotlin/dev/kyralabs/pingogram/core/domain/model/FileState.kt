package dev.kyralabs.pingogram.core.domain.model

/**
 * How much of a TDLib file is on this device (`FileRepository`, `docs/ARCHITECTURE.md` Files and media). Sizes are
 * in bytes; a size of 0 means TDLib does not know it yet.
 */
sealed interface FileState {
    /** Nothing downloaded yet; TDLib can download (or generate) the file only if [canBeDownloaded]. */
    data class NotDownloaded(
        val size: Long,
        val canBeDownloaded: Boolean,
    ) : FileState

    /**
     * Partly downloaded: [downloadedSize] of [size]. [isActive] is false while the download is stopped; downloading
     * again continues from there.
     */
    data class Downloading(
        val downloadedSize: Long,
        val size: Long,
        val isActive: Boolean,
    ) : FileState {
        /** From 0 to 1; 0 while the size is unknown. */
        val progress: Float
            get() = if (size > 0) (downloadedSize.toFloat() / size).coerceIn(0f, 1f) else 0f
    }

    /** Complete at [path], in TDLib's files directory or, for files sent from this device, where they were. */
    data class Downloaded(
        val path: String,
        val size: Long,
    ) : FileState {
        // The path ends in the file's name, e.g. that of a document someone sent.
        override fun toString() = "Downloaded(size=$size, …)"
    }
}

/** How soon TDLib downloads a file, among the downloads it runs (`docs/PERFORMANCE.md` §3). */
enum class DownloadPriority {
    /** On screen now. */
    Visible,

    /** About to come into view. */
    Prefetch,

    /** Needed later, e.g. a download that continues in the background. */
    Background,
}
