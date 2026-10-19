package dev.kyralabs.pingogram.feature.settings.ui.photo

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.feature.settings.ui.photo.ProfilePhotoFiles.Companion.MAX_SIDE_PX
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * [ProfilePhotoFiles] on the platform. The picked image is decoded by the app's Coil [imageLoader], which reads content
 * URIs, turns the image upright as its EXIF orientation says, and decodes it no larger than [MAX_SIDE_PX]; it is
 * neither kept in Coil's memory cache nor shown. The copy is a JPEG in `cacheDir/profile_photos`, written on the [io]
 * dispatcher under a random name, and its canonical path is what TDLib gets: TDLib resolves the paths it is given, and
 * reports the upload's progress for the resolved one.
 *
 * Nothing about the image or its path is logged.
 */
internal class AndroidProfilePhotoFiles
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:Dispatcher(IO) private val io: CoroutineDispatcher,
        private val imageLoader: ImageLoader,
    ) : ProfilePhotoFiles {
        private val directory: File get() = File(context.cacheDir, DIRECTORY)

        override suspend fun prepare(uri: String): String? {
            withContext(io) { directory.deleteRecursively() }
            val request =
                ImageRequest
                    .Builder(context)
                    .data(uri)
                    .size(MAX_SIDE_PX)
                    .scale(Scale.FIT)
                    // Never larger than the image itself.
                    .precision(Precision.INEXACT)
                    // Compressed below, which needs its pixels in memory.
                    .allowHardware(false)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build()
            val image = (imageLoader.execute(request) as? SuccessResult)?.image ?: return null
            return withContext(io) { writeJpeg(image.toBitmap().fittingWithin(MAX_SIDE_PX)) }
        }

        override suspend fun delete(path: String) {
            withContext(io) { File(path).delete() }
        }

        private fun writeJpeg(bitmap: Bitmap): String? {
            val directory = directory
            if (!directory.isDirectory && !directory.mkdirs()) return null
            val file = File(directory, "${UUID.randomUUID()}.$EXTENSION")
            val written =
                try {
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                } catch (_: IOException) {
                    false
                }
            if (!written) file.delete()
            return if (written) file.canonicalPath else null
        }

        private companion object {
            const val DIRECTORY = "profile_photos"
            const val EXTENSION = "jpg"

            /** Telegram compresses the photo again; this keeps the upload small without visible loss. */
            const val JPEG_QUALITY = 90
        }
    }

/**
 * This bitmap, scaled down so its longest side is at most [maxSide] pixels, keeping its aspect ratio: Coil decodes a
 * little larger now and then, rounding its sample sizes.
 */
private fun Bitmap.fittingWithin(maxSide: Int): Bitmap {
    val (scaledWidth, scaledHeight) = fitWithin(width, height, maxSide)
    return if (scaledWidth == width && scaledHeight == height) this else scale(scaledWidth, scaledHeight)
}

/** [width] × [height] scaled down so the longest side is at most [maxSide], keeping the aspect ratio; at least 1 px. */
internal fun fitWithin(
    width: Int,
    height: Int,
    maxSide: Int,
): Pair<Int, Int> {
    val longest = maxOf(width, height)
    if (longest <= maxSide) return width to height
    val factor = maxSide.toDouble() / longest
    return (width * factor).roundToInt().coerceAtLeast(1) to (height * factor).roundToInt().coerceAtLeast(1)
}
