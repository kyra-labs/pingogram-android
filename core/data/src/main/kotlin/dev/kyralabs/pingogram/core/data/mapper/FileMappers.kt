package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import org.drinkless.tdlib.TdApi

/**
 * How much of the file is on this device ([TdApi.File.local]). Anything downloaded but incomplete is
 * [FileState.Downloading], also while stopped, so its progress stays visible. Sizes fall back to TDLib's estimate
 * while the exact size is unknown.
 */
internal fun TdApi.File.toFileState(): FileState {
    val knownSize = if (size > 0) size else expectedSize
    return when {
        local.isDownloadingCompleted -> {
            FileState.Downloaded(
                path = local.path.orEmpty(),
                size = if (knownSize > 0) knownSize else local.downloadedSize,
            )
        }

        local.isDownloadingActive || local.downloadedSize > 0 -> {
            FileState.Downloading(
                downloadedSize = local.downloadedSize,
                size = knownSize,
                isActive = local.isDownloadingActive,
            )
        }

        else -> {
            FileState.NotDownloaded(size = knownSize, canBeDownloaded = local.canBeDownloaded)
        }
    }
}

/** TDLib's download priority, 1–32, higher first (`docs/PERFORMANCE.md` §3). */
internal val DownloadPriority.tdlibPriority: Int
    get() =
        when (this) {
            DownloadPriority.Visible -> VISIBLE_PRIORITY
            DownloadPriority.Prefetch -> PREFETCH_PRIORITY
            DownloadPriority.Background -> BACKGROUND_PRIORITY
        }

private const val VISIBLE_PRIORITY = 32
private const val PREFETCH_PRIORITY = 16
private const val BACKGROUND_PRIORITY = 1
