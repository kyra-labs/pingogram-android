package dev.kyralabs.pingogram.core.ui.image

import coil3.key.Keyer
import coil3.request.Options

/**
 * A TDLib file shown as an image, by its id (`FileRef.id`): the `model` of a Coil `AsyncImage` or `ImageRequest`
 * (`docs/ARCHITECTURE.md`, Files and media). The app's `ImageLoader` loads it with [TdlibFileFetcher] from TDLib's own
 * files and caches the decoded image in memory under [TdlibFileImageKeyer]'s key; nothing is cached on disk twice.
 *
 * With [download] (the default) the fetcher downloads the file if it is not on the device yet; without, it only waits
 * for it: a photo that automatic download leaves for a tap (`docs/PERFORMANCE.md` §7) shows once something else
 * downloaded it.
 *
 * File ids belong to one TDLib instance: after logout the next session reuses them for other files, so the app clears
 * the memory cache when TDLib closes.
 */
data class TdlibFileImage(
    val fileId: Int,
    val download: Boolean = true,
)

/**
 * Keys the memory cache by file id (Coil adds the requested size), whether or not the request downloads. Without a
 * keyer Coil would not cache [TdlibFileImage]s in memory at all.
 */
class TdlibFileImageKeyer : Keyer<TdlibFileImage> {
    override fun key(
        data: TdlibFileImage,
        options: Options,
    ): String = "tdlib-file:${data.fileId}"
}
