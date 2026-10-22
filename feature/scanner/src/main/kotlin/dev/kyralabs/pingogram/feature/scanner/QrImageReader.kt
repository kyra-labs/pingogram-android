package dev.kyralabs.pingogram.feature.scanner

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import kotlin.math.max

/** Reads the QR code in an image the user picked ("Scan from image"). */
internal interface QrImageReader {
    /** The text of the QR code in the image at [uri], null if it shows none; [IOException] if it cannot be read. */
    suspend fun read(uri: String): String?
}

/**
 * [QrImageReader] on the platform's `BitmapFactory`: the image the photo picker handed over, decoded on the IO
 * dispatcher at most [MAX_SIDE] pixels on its longer side (codes stay readable, memory stays small), then read by
 * [QrDecoder]. The picker's grant covers reading it; no permission is needed, and the image is not kept.
 */
internal class AndroidQrImageReader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @Dispatcher(IO) private val io: CoroutineDispatcher,
    ) : QrImageReader {
        override suspend fun read(uri: String): String? =
            withContext(io) {
                val parsed = uri.toUri()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image")
                val options =
                    BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
                val bitmap =
                    context.contentResolver
                        .openInputStream(parsed)
                        ?.use { BitmapFactory.decodeStream(it, null, options) }
                        ?: throw IOException("Not an image")
                try {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    QrDecoder().decodeArgb(pixels, bitmap.width, bitmap.height)
                } finally {
                    bitmap.recycle()
                }
            }
    }

/** The power of two that brings the longer side of a [width] × [height] image to at most [MAX_SIDE]. */
internal fun sampleSize(
    width: Int,
    height: Int,
): Int {
    var size = 1
    while (max(width, height) / size > MAX_SIDE) size *= 2
    return size
}

/** The longest side an image is read at. */
private const val MAX_SIDE = 1600

@Module
@InstallIn(ViewModelComponent::class)
internal interface ScannerModule {
    @Binds
    fun qrImageReader(reader: AndroidQrImageReader): QrImageReader
}
