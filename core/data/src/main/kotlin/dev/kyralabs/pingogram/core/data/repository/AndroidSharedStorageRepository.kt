package dev.kyralabs.pingogram.core.data.repository

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
import dev.kyralabs.pingogram.core.domain.model.SharedFile
import dev.kyralabs.pingogram.core.domain.repository.SharedStorageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

/**
 * [SharedStorageRepository] on the platform, copying on the [io] dispatcher into a `Pingogram` folder: photos (and
 * real GIFs) in `Pictures`, videos and MP4 GIFs in `Movies`, anything saved to Downloads in `Download`.
 *
 * - **Android 10 and later**: through `MediaStore` (`Images`, `Video`, `Downloads`), without any permission: the item
 *   is inserted pending (`IS_PENDING`), so other apps do not see it half written, filled, then published; a failed copy
 *   removes it. `MediaStore` numbers a name that is taken.
 * - **Android 8–9**: `MediaStore` cannot place files there yet, so the file is copied into the public directory,
 *   which needs `WRITE_EXTERNAL_STORAGE` (declared for these versions only), and the media scanner indexes it. An
 *   existing file of the same name is kept: the copy gets " (1)", " (2)" … added.
 *
 * Nothing about the file is logged.
 */
class AndroidSharedStorageRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:Dispatcher(IO) private val io: CoroutineDispatcher,
    ) : SharedStorageRepository {
        override fun needsStoragePermission(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED

        override suspend fun saveToGallery(file: SharedFile): Boolean =
            save(file) { mimeType ->
                if (mimeType.startsWith(IMAGE_TYPE_PREFIX)) Collection.Pictures else Collection.Movies
            }

        override suspend fun saveToDownloads(file: SharedFile): Boolean = save(file) { Collection.Downloads }

        /** Copies [file] into the collection [collectionOf] its type picks; false if it could not be. */
        private suspend fun save(
            file: SharedFile,
            collectionOf: (mimeType: String) -> Collection,
        ): Boolean =
            withContext(io) {
                val source = File(file.path)
                val mimeType = mimeTypeOf(file.path) ?: file.fallbackMimeType
                val collection = collectionOf(mimeType)
                val name = "${file.baseName}.${extensionOf(source, mimeType)}"
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        insertIntoMediaStore(source, name, mimeType, collection)
                    } else {
                        copyIntoPublicDirectory(source, name, mimeType, collection)
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
            collection: Collection,
        ) {
            val uri =
                when (collection) {
                    Collection.Pictures -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    Collection.Movies -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    Collection.Downloads -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${collection.directory}/$FOLDER")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            val resolver = context.contentResolver
            val item = resolver.insert(uri, values) ?: throw IOException("MediaStore refused the item")
            try {
                resolver.copy(source, item)
                resolver.update(item, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (failure: IOException) {
                resolver.delete(item, null, null)
                throw failure
            }
        }

        // Environment.getExternalStoragePublicDirectory is deprecated from Android 10, where MediaStore replaces it;
        // this runs only before that, where it is the way to put a file into Pictures, Movies or Download.
        @Suppress("DEPRECATION")
        private fun copyIntoPublicDirectory(
            source: File,
            name: String,
            mimeType: String,
            collection: Collection,
        ) {
            val folder = File(Environment.getExternalStoragePublicDirectory(collection.directory), FOLDER)
            val target = copyIntoFolder(source, folder, name)
            MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(mimeType), null)
        }

        /** The file's own extension, else the one of [mimeType], else `jpg` for images and `bin` for the rest. */
        private fun extensionOf(
            file: File,
            mimeType: String,
        ): String =
            file.extension.takeIf { it.isNotEmpty() }
                ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                ?: when {
                    mimeType.startsWith(IMAGE_TYPE_PREFIX) -> IMAGE_EXTENSION
                    mimeType.startsWith(VIDEO_TYPE_PREFIX) -> VIDEO_EXTENSION
                    else -> BINARY_EXTENSION
                }

        /** Where a file goes: the public directory of its kind. */
        private enum class Collection(
            val directory: String,
        ) {
            Pictures(Environment.DIRECTORY_PICTURES),
            Movies(Environment.DIRECTORY_MOVIES),
            Downloads(Environment.DIRECTORY_DOWNLOADS),
        }

        private companion object {
            /** The folder in Pictures, Movies and Download. */
            const val FOLDER = "Pingogram"
            const val IMAGE_TYPE_PREFIX = "image/"
            const val VIDEO_TYPE_PREFIX = "video/"
            const val VIDEO_EXTENSION = "mp4"
            const val IMAGE_EXTENSION = "jpg"
            const val BINARY_EXTENSION = "bin"
        }
    }

/** The MIME type the platform knows for the extension of [path], e.g. `image/jpeg` for `.jpg`; null if none. */
private fun mimeTypeOf(path: String): String? {
    val extension = path.substringAfterLast('/').substringAfterLast('.', missingDelimiterValue = "")
    if (extension.isEmpty()) return null
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase(Locale.ROOT))
}

/** Copies [source] into the item at [uri], replacing what it holds. */
private fun ContentResolver.copy(
    source: File,
    uri: Uri,
) {
    val output = openOutputStream(uri, "w") ?: throw IOException("The item cannot be written")
    output.use { stream -> source.inputStream().use { it.copyTo(stream) } }
}

/**
 * Copies [source] into the [folder] directory, made if missing, as [name], or "name (1).ext", "name (2).ext" … where a
 * file has that name already, and returns the copy.
 */
internal fun copyIntoFolder(
    source: File,
    folder: File,
    name: String,
): File {
    if (!folder.isDirectory && !folder.mkdirs()) throw IOException("No folder")
    val target = unusedFile(folder, name)
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
