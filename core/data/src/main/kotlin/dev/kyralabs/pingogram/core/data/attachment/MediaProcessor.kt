package dev.kyralabs.pingogram.core.data.attachment

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.IOException

/**
 * The image and video work of preparing attachments, on Android's decoders: a seam, so the preparation runs in JVM
 * tests with a fake, while this is tested on Robolectric. Blocking and memory-hungry: one at a time, off the main
 * thread.
 */
internal interface MediaProcessor {
    /** The stored size and EXIF orientation of the image in [file]; null when Android cannot decode it. */
    fun imageInfo(file: File): ImageInfo?

    /**
     * Decodes the image in [source] and writes it to [target] as [plan] says (upright, scaled), as a JPEG without
     * metadata: no location, no camera details. False when it cannot be decoded or written.
     */
    fun writePhoto(
        source: File,
        plan: PhotoPlan,
        target: File,
        quality: Int = JPEG_QUALITY,
    ): Boolean

    /**
     * Copies the JPEG in [source] to [target] without its metadata ([JpegMetadata]), its image data byte for byte, then
     * writes its EXIF orientation back alone, so it stays upright. False when it is no JPEG or cannot be written.
     */
    fun copyJpegWithoutMetadata(
        source: File,
        target: File,
    ): Boolean

    /**
     * Decodes the image in [source] and writes it to [target] as [plan] says (upright, scaled) as a lossy WebP that
     * keeps its transparency, for a sticker. False when it cannot be decoded or written.
     */
    fun writeSticker(
        source: File,
        plan: PhotoPlan,
        target: File,
    ): Boolean

    /** The size, rotation and length of the video in [file]; null when Android cannot read it as a video. */
    fun videoInfo(file: File): VideoInfo?

    /**
     * Writes a still from the start of the video in [source], upright, scaled to [width] × [height], to [target] as a
     * JPEG. False when no still can be taken.
     */
    fun writeVideoThumbnail(
        source: File,
        width: Int,
        height: Int,
        target: File,
    ): Boolean

    /** The length and tags of the audio in [file]; null when Android cannot read it as audio without video. */
    fun audioInfo(file: File): AudioInfo? = null
}

/** A song as stored: [durationMillis] long, with its [title] and [artist] tags, empty where it has none. */
internal data class AudioInfo(
    val durationMillis: Long,
    val title: String,
    val artist: String,
) {
    override fun toString() = "AudioInfo(durationMillis=$durationMillis, …)"
}

/** An image as stored: [width] × [height] pixels, seen in [orientation]. */
internal data class ImageInfo(
    val width: Int,
    val height: Int,
    val orientation: Orientation,
) {
    /** How many times longer than wide it is, its longer side over its shorter one. */
    val aspect: Float get() = maxOf(width, height).toFloat() / minOf(width, height).coerceAtLeast(1)
}

/**
 * A video as stored: [width] × [height] pixels, played turned clockwise by [rotation] degrees, [durationMillis] long.
 */
internal data class VideoInfo(
    val width: Int,
    val height: Int,
    val rotation: Int,
    val durationMillis: Long,
) {
    /** The width and height as it plays. */
    val displaySize: Pair<Int, Int>
        get() = if (rotation % HALF_TURN != 0) height to width else width to height
}

/**
 * [MediaProcessor] on `BitmapFactory`, `ExifInterface` and `MediaMetadataRetriever`. A photo is decoded at the
 * plan's sample size (at most twice the target in each direction), then turned, mirrored and scaled in one pass, and
 * drawn on white when it has transparency, which JPEG does not keep.
 */
internal class AndroidMediaProcessor : MediaProcessor {
    override fun imageInfo(file: File): ImageInfo? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return ImageInfo(bounds.outWidth, bounds.outHeight, Orientation.fromExif(exifOrientation(file)))
    }

    override fun writePhoto(
        source: File,
        plan: PhotoPlan,
        target: File,
        quality: Int,
    ): Boolean {
        val options = BitmapFactory.Options().apply { inSampleSize = plan.sampleSize }
        val decoded = BitmapFactory.decodeFile(source.path, options) ?: return false
        val transformed = decoded.transformed(plan.orientation, plan.width, plan.height)
        if (transformed !== decoded) decoded.recycle()
        val photo = transformed.opaque()
        return try {
            photo.writeJpeg(target, quality)
        } finally {
            photo.recycle()
        }
    }

    override fun writeSticker(
        source: File,
        plan: PhotoPlan,
        target: File,
    ): Boolean {
        val options = BitmapFactory.Options().apply { inSampleSize = plan.sampleSize }
        val decoded = BitmapFactory.decodeFile(source.path, options) ?: return false
        val sticker = decoded.transformed(plan.orientation, plan.width, plan.height)
        if (sticker !== decoded) decoded.recycle()
        return try {
            target.outputStream().use { sticker.compress(webp(), STICKER_QUALITY, it) }
        } catch (_: IOException) {
            false
        } finally {
            sticker.recycle()
        }
    }

    @Suppress("ExifInterface") // Lint's warning is about the platform class before Android 7; minSdk is 26.
    override fun copyJpegWithoutMetadata(
        source: File,
        target: File,
    ): Boolean {
        val header = ByteArray(JPEG_HEADER).also { bytes -> source.inputStream().use { it.read(bytes) } }
        if (!JpegMetadata.isJpeg(header)) return false
        val orientation = exifOrientation(source)
        return try {
            source.inputStream().use { input -> target.outputStream().use { JpegMetadata.strip(input, it) } }
            if (orientation != android.media.ExifInterface.ORIENTATION_NORMAL) {
                android.media.ExifInterface(target.path).apply {
                    setAttribute(android.media.ExifInterface.TAG_ORIENTATION, orientation.toString())
                    saveAttributes()
                }
            }
            true
        } catch (_: IOException) {
            target.delete()
            false
        }
    }

    override fun videoInfo(file: File): VideoInfo? =
        retrieving(file) { retriever ->
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == HAS_TRACK
            val width = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (!hasVideo || width <= 0 || height <= 0) return@retrieving null
            VideoInfo(
                width = width,
                height = height,
                rotation = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),
                durationMillis =
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?: 0L,
            )
        }

    override fun writeVideoThumbnail(
        source: File,
        width: Int,
        height: Int,
        target: File,
    ): Boolean =
        retrieving(source) { retriever ->
            val frame = retriever.firstFrame() ?: return@retrieving false
            // Frames come upright from current Android versions; older ones return them as stored.
            val rotation = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val landscapeFrame = frame.width > frame.height
            val stillTurned = rotation % HALF_TURN != 0 && landscapeFrame != (width > height)
            val orientation = if (stillTurned) Orientation(rotation, mirrored = false) else Orientation.Normal
            val still = frame.transformed(orientation, width, height)
            if (still !== frame) frame.recycle()
            try {
                still.writeJpeg(target)
            } finally {
                still.recycle()
            }
        } == true

    override fun audioInfo(file: File): AudioInfo? =
        retrieving(file) { retriever ->
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == HAS_TRACK
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == HAS_TRACK
            val duration =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (!hasAudio || hasVideo || duration <= 0L) return@retrieving null
            AudioInfo(
                durationMillis = duration,
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty().trim(),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().trim(),
            )
        }

    /**
     * EXIF's orientation tag of [file]; upright when it has none or cannot be read.
     *
     * The platform's `ExifInterface` rather than androidx's: the androidx library is not a dependency of this module
     * (the app gets it only at run time, through Coil), and from Android 7 the platform's is the same implementation.
     * Named in full here, so that Lint's suppression below covers every use.
     */
    @Suppress("ExifInterface") // Lint's warning is about the platform class before Android 7; minSdk is 26.
    private fun exifOrientation(file: File): Int {
        val upright = android.media.ExifInterface.ORIENTATION_NORMAL
        return try {
            android.media.ExifInterface(file.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, upright)
        } catch (_: IOException) {
            upright
        }
    }

    /** A frame near the start, scaled down while it is decoded where Android can (8.1 and later). */
    private fun MediaMetadataRetriever.firstFrame(): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, FRAME_SIDE, FRAME_SIDE)
        } else {
            getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }

    /** [block] with a retriever on [file], released after; null when Android cannot open the file. */
    @Suppress("TooGenericExceptionCaught") // The retriever throws IllegalArgumentException and RuntimeException.
    private inline fun <T> retrieving(
        file: File,
        block: (MediaMetadataRetriever) -> T?,
    ): T? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            block(retriever)
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    private companion object {
        /** What the retriever answers when a file has a track of the kind asked about. */
        const val HAS_TRACK = "yes"

        /** How many bytes start a JPEG ([JpegMetadata.isJpeg]). */
        const val JPEG_HEADER = 3

        /** The largest side of a frame decoded for a thumbnail: twice the thumbnail's, for a sharp downscale. */
        const val FRAME_SIDE = MAX_THUMBNAIL_SIDE * 2
    }
}

/** The metadata [key] of the file as a number; 0 when it has none. */
private fun MediaMetadataRetriever.metadataInt(key: Int): Int = extractMetadata(key)?.toIntOrNull() ?: 0

/** This bitmap mirrored and turned as [orientation] says, then scaled to [width] × [height]; itself if unchanged. */
private fun Bitmap.transformed(
    orientation: Orientation,
    width: Int,
    height: Int,
): Bitmap {
    val uprightWidth = if (orientation.swapsSides) this.height else this.width
    val uprightHeight = if (orientation.swapsSides) this.width else this.height
    if (orientation == Orientation.Normal && uprightWidth == width && uprightHeight == height) return this
    val matrix =
        Matrix().apply {
            if (orientation.mirrored) postScale(-1f, 1f)
            postRotate(orientation.rotation.toFloat())
            postScale(width.toFloat() / uprightWidth, height.toFloat() / uprightHeight)
        }
    return Bitmap.createBitmap(this, 0, 0, this.width, this.height, matrix, true)
}

/** Lossy WebP, which keeps transparency: its own name from Android 11, the deprecated one before. */
private fun webp(): Bitmap.CompressFormat =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        @Suppress("DEPRECATION") // WEBP_LOSSY exists from Android 11 only; WEBP is lossy until then.
        Bitmap.CompressFormat.WEBP
    }

/** The WebP quality of a sticker: small enough for Telegram's 512 KB, without visible artifacts. */
private const val STICKER_QUALITY = 90

/** This bitmap on a white background if it has transparency (recycling it), else itself. */
private fun Bitmap.opaque(): Bitmap {
    if (!hasAlpha()) return this
    val opaque = createBitmap(width, height)
    Canvas(opaque).apply {
        drawColor(Color.WHITE)
        drawBitmap(this@opaque, 0f, 0f, null)
    }
    recycle()
    return opaque
}

/** Writes this bitmap to [target] as a JPEG of [quality]; false when that fails. */
private fun Bitmap.writeJpeg(
    target: File,
    quality: Int = JPEG_QUALITY,
): Boolean =
    try {
        target.outputStream().use { compress(Bitmap.CompressFormat.JPEG, quality, it) }
    } catch (_: IOException) {
        false
    }

private const val HALF_TURN = 180
