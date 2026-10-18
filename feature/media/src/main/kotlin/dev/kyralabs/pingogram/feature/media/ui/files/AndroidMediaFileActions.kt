package dev.kyralabs.pingogram.feature.media.ui.files

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.ui.file.mimeTypeOfFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * [MediaFileActions] on the platform: photos (and real GIFs) go to `Pictures/Pingogram`, videos and MP4 GIFs to
 * `Movies/Pingogram`, copied on the [io] dispatcher.
 *
 * - **Android 10 and later**: through `MediaStore`, without any permission: the item is inserted pending
 *   (`IS_PENDING`), so the gallery does not show it half written, filled, then published; a failed copy removes it.
 * - **Android 8–9**: `MediaStore` cannot place files there yet, so the file is copied into the public directory,
 *   which needs `WRITE_EXTERNAL_STORAGE` (declared for these versions only), and the media scanner adds it to the
 *   gallery. An existing file of the same name is kept: the copy gets " (1)", " (2)" … added.
 *
 * Nothing about the file is logged.
 */
internal class AndroidMediaFileActions
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:Dispatcher(IO) private val io: CoroutineDispatcher,
    ) : MediaFileActions {
        override fun needsStoragePermission(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED

        override suspend fun saveToGallery(item: GalleryItem): Boolean =
            withContext(io) {
                val source = File(item.path)
                val mimeType = mimeTypeOfFile(item.path) ?: item.fallbackMimeType
                val isImage = mimeType.startsWith(IMAGE_TYPE_PREFIX)
                val name = "${item.baseName}.${extensionOf(source, mimeType)}"
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        insertIntoMediaStore(source, name, mimeType, isImage)
                    } else {
                        copyIntoPublicDirectory(source, name, mimeType, isImage)
                    }
                    true
                } catch (_: IOException) {
                    false
                } catch (_: SecurityException) {
                    // The storage permission was revoked meanwhile.
                    false
                } catch (_: IllegalStateException) {
                    // MediaStore refused the values, e.g. on a volume that is not mounted.
                    false
                }
            }

        @RequiresApi(Build.VERSION_CODES.Q)
        private fun insertIntoMediaStore(
            source: File,
            name: String,
            mimeType: String,
            isImage: Boolean,
        ) {
            val collection =
                if (isImage) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${publicDirectory(isImage)}/$ALBUM")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: throw IOException("The gallery refused the item")
            try {
                resolver.copy(source, uri)
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (failure: IOException) {
                resolver.delete(uri, null, null)
                throw failure
            }
        }

        // Environment.getExternalStoragePublicDirectory is deprecated from Android 10, where MediaStore replaces it;
        // this runs only before that, where it is the way to put a file into Pictures or Movies.
        @Suppress("DEPRECATION")
        private fun copyIntoPublicDirectory(
            source: File,
            name: String,
            mimeType: String,
            isImage: Boolean,
        ) {
            val album = File(Environment.getExternalStoragePublicDirectory(publicDirectory(isImage)), ALBUM)
            val target = copyIntoAlbum(source, album, name)
            MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(mimeType), null)
        }

        private fun publicDirectory(isImage: Boolean): String =
            if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES

        /** The file's own extension, else the one of [mimeType], else `jpg` or `mp4`. */
        private fun extensionOf(
            file: File,
            mimeType: String,
        ): String =
            file.extension.takeIf { it.isNotEmpty() }
                ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                ?: if (mimeType.startsWith(IMAGE_TYPE_PREFIX)) IMAGE_EXTENSION else VIDEO_EXTENSION

        private companion object {
            /** The album in Pictures and Movies. */
            const val ALBUM = "Pingogram"
            const val IMAGE_TYPE_PREFIX = "image/"
            const val VIDEO_EXTENSION = "mp4"
            const val IMAGE_EXTENSION = "jpg"
        }
    }

/** Copies [source] into the item at [uri], replacing what it holds. */
private fun ContentResolver.copy(
    source: File,
    uri: Uri,
) {
    val output = openOutputStream(uri, "w") ?: throw IOException("The gallery item cannot be written")
    output.use { stream -> source.inputStream().use { it.copyTo(stream) } }
}

/**
 * Copies [source] into the [album] directory, made if missing, as [name], or "name (1).ext", "name (2).ext" … where a
 * file has that name already, and returns the copy.
 */
internal fun copyIntoAlbum(
    source: File,
    album: File,
    name: String,
): File {
    if (!album.isDirectory && !album.mkdirs()) throw IOException("No album directory")
    val target = unusedFile(album, name)
    source.copyTo(target)
    return target
}

/** [name] in [directory], or "name (1).ext", "name (2).ext" … if a file has it. */
private fun unusedFile(
    directory: File,
    name: String,
): File {
    val base = name.substringBeforeLast('.')
    val extension = name.substringAfterLast('.', missingDelimiterValue = "")
    return generateSequence(0) { it + 1 }
        .map { copy ->
            val numbered = if (copy == 0) base else "$base ($copy)"
            File(directory, if (extension.isEmpty()) numbered else "$numbered.$extension")
        }.first { !it.exists() }
}
