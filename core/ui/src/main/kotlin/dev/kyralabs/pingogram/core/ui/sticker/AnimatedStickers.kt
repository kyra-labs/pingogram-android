package dev.kyralabs.pingogram.core.ui.sticker

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.graphics.createBitmap
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Plays animated (TGS) stickers in the conversation and the sticker panel (`REQUIREMENTS.md` FR-C4, FR-C5;
 * `docs/PERFORMANCE.md` §4), and animated custom emoji in texts ([CustomEmojiRenderer]), one instance per process,
 * which `:app` provides through [LocalAnimatedStickers]:
 *
 * - **Files**: a sticker's file is downloaded from TDLib at visible priority when it first shows ([files]), then
 *   decoded by [decoder] on [renderDispatcher]; decoded animations stay in an LRU of [MAX_DECODED], by file id.
 * - **Frame cache**, by sticker and size: the first frame of each sticker at each size it shows at, at most
 *   [FIRST_FRAMES_BYTES] of bitmaps, so a sticker that scrolls back, and every sticker beyond the [slots], shows at
 *   once and still. A whole animation is not kept: a 72 dp panel sticker at 30 frames per second would take about
 *   17 MB for 3 seconds. Playing ones render their frames ahead of time on [renderDispatcher] instead, into two bitmaps
 *   of their own (`AnimatedSticker`).
 * - **At most [maxAnimating] play at once** ([slots]), stickers and custom emoji alike (each custom emoji that plays
 *   counts as one); the others show their first frame until one stops. They play at
 *   most [MAX_FRAMES_PER_SECOND] frames per second, only while on screen and while the window has the focus, and not at
 *   all with animations off (or [animate] false, as in previews and screenshots). While [still] is true (power saving,
 *   `PowerSaving.stillStickers`), none play: each shows its first frame, and plays on once it is false again.
 *
 * File ids belong to one TDLib instance: [clear] after TDLib closes.
 */
@Stable
class AnimatedStickers(
    private val files: FileRepository,
    private val decoder: StickerAnimationDecoder,
    internal val renderDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(RENDER_THREADS),
    maxAnimating: Int = MAX_ANIMATING,
    internal val animate: Boolean = true,
    internal val still: StateFlow<Boolean> = MutableStateFlow(false),
) {
    /** One permit per sticker that may play now. */
    internal val slots = Semaphore(maxAnimating)

    private val decoded = LruCache<Int, StickerAnimation>(MAX_DECODED)
    private val decoding = Mutex()

    private val firstFrames =
        object : LruCache<FrameKey, Bitmap>(FIRST_FRAMES_BYTES) {
            override fun sizeOf(
                key: FrameKey,
                value: Bitmap,
            ) = value.allocationByteCount
        }

    /**
     * The animation of TDLib file [fileId], downloading the file first if needed; null when it cannot be downloaded or
     * holds no animation. Decodes one file at a time, so a sticker shown twice is decoded once.
     */
    internal suspend fun animation(fileId: Int): StickerAnimation? =
        decoded[fileId] ?: downloadedPath(fileId)?.let { path ->
            decoding.withLock {
                decoded[fileId]
                    ?: withContext(renderDispatcher) { decoder.decode(File(path)) }?.also { decoded.put(fileId, it) }
            }
        }

    /** The first frame of [animation] (file [fileId]) at [width] × [height] pixels, from the frame cache. */
    internal suspend fun firstFrame(
        fileId: Int,
        animation: StickerAnimation,
        width: Int,
        height: Int,
    ): Bitmap {
        val key = FrameKey(fileId, width, height)
        firstFrames[key]?.let { return it }
        val bitmap =
            withContext(renderDispatcher) {
                createBitmap(width, height).also { animation.newRenderer().render(0, it) }
            }
        firstFrames.put(key, bitmap)
        return bitmap
    }

    /** The first frame of file [fileId] at [width] × [height] pixels if the frame cache holds it; never waits. */
    internal fun cachedFirstFrame(
        fileId: Int,
        width: Int,
        height: Int,
    ): Bitmap? = firstFrames[FrameKey(fileId, width, height)]

    /** How many first frames the frame cache holds, of every sticker and size; for tests. */
    internal val firstFrameCount: Int get() = firstFrames.snapshot().size

    /** Forgets every animation and frame: TDLib closed, and the next instance reuses file ids for other files. */
    fun clear() {
        decoded.evictAll()
        firstFrames.evictAll()
    }

    /**
     * The local path of file [fileId] once it is downloaded, starting the download if it has not started; null if it
     * cannot be downloaded or the download stops.
     */
    private suspend fun downloadedPath(fileId: Int): String? {
        var requested = false
        var started = false
        return files
            .observe(fileId)
            .mapNotNull { state ->
                when (state) {
                    is FileState.Downloaded -> {
                        Download(state.path.ifEmpty { null })
                    }

                    is FileState.NotDownloaded -> {
                        when {
                            !state.canBeDownloaded -> {
                                Download(path = null)
                            }

                            requested -> {
                                null
                            }

                            else -> {
                                requested = true
                                val result = files.download(fileId, DownloadPriority.Visible)
                                if (result is Outcome.Failure) Download(path = null) else null
                            }
                        }
                    }

                    is FileState.Downloading -> {
                        if (state.isActive) started = true
                        // Stopped after it ran: nothing more will come.
                        if (started && !state.isActive) Download(path = null) else null
                    }
                }
            }.first()
            .path
    }

    /** How waiting for a file ended: its [path], or null if it will not be downloaded. */
    private class Download(
        val path: String?,
    )

    private data class FrameKey(
        val fileId: Int,
        val width: Int,
        val height: Int,
    )

    companion object {
        /** `docs/PERFORMANCE.md` §4: at most this many stickers animate at once. */
        const val MAX_ANIMATING = 8

        /** The most frames per second a sticker plays: TGS stickers are made at 60, and every other frame will do. */
        const val MAX_FRAMES_PER_SECOND = 30

        /** Threads that decode and render; drawing a frame takes a few milliseconds at most. */
        private const val RENDER_THREADS = 2

        private const val MAX_DECODED = 48

        /** About 50 first frames of panel stickers, or 12 of stickers in messages, at 3 pixels per dp. */
        private const val FIRST_FRAMES_BYTES = 8 * 1024 * 1024
    }
}

/**
 * The [AnimatedStickers] of the app, which `:app` provides; null in previews and screenshots, where animated stickers
 * show their thumbnail, as they do before their file is downloaded.
 */
val LocalAnimatedStickers = staticCompositionLocalOf<AnimatedStickers?> { null }
