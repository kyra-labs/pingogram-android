package dev.kyralabs.pingogram.core.data.attachment

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.domain.model.Attachment
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's private cache directories for files on their way to TDLib (`REQUIREMENTS.md` FR-C5, §6.2), under
 * `cacheDir`, which is never backed up:
 *
 * - `uploads/`: one directory per picked file, holding its copy (a document under its original name, which TDLib sends
 *   as the file's name), the photo made from it, or a video's thumbnail; or per recorded voice message. An attachment
 *   is deleted with its directory.
 * - `camera/`: the files the camera app writes photos into, which `:app`'s `FileProvider` serves
 *   (`res/xml/file_paths.xml`, `cache-path` "camera").
 *
 * Only files inside them are ever deleted here; paths are compared canonically, as TDLib reports the real paths of the
 * files it sends. Blocking IO: call it off the main thread.
 */
@Singleton
class UploadFiles internal constructor(
    private val cacheDir: () -> File,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this({ context.cacheDir })

    private val uploads: File get() = File(cacheDir(), UPLOADS)

    private val camera: File get() = File(cacheDir(), CAMERA)

    /** A new, empty directory in `uploads/` for one picked file, or one recorded voice message. */
    fun newUploadDirectory(): File =
        File(uploads, UUID.randomUUID().toString()).also { directory ->
            if (!directory.mkdirs()) throw IOException("Cannot create an upload directory")
        }

    /** A new, empty file in `camera/` for the camera app to write a photo into. */
    fun newCameraFile(): File {
        camera.mkdirs()
        return File(camera, "${UUID.randomUUID()}.jpg").also { file ->
            if (!file.createNewFile()) throw IOException("Cannot create a camera file")
        }
    }

    /** The file of `camera/` at [path]; null for any other path. */
    fun cameraFile(path: String): File? = File(path).takeIf { it.isDirectlyIn(camera) }

    /** Whether [path] is a file in `uploads/`, i.e. one of the copies TDLib uploads from. */
    fun isUpload(path: String): Boolean = path.isNotEmpty() && File(path).isUnder(uploads)

    /** Deletes the copies of [attachments], with their directories. */
    fun delete(attachments: Collection<Attachment>) = deleteUploads(attachments.map { it.path })

    /**
     * Deletes the files at [paths] that are in `uploads/`, each with the directory it was prepared in, which holds
     * nothing else of use (the copy a photo was made from, a video's thumbnail).
     */
    fun deleteUploads(paths: Collection<String>) {
        for (path in paths) {
            val file = File(path)
            if (!file.isUnder(uploads)) continue
            val directory = file.parentFile?.takeIf { it.isDirectlyIn(uploads) }
            if (directory != null) directory.deleteRecursively() else file.delete()
        }
    }

    /** Deletes what is older than [maxAgeMillis] in both directories: copies and camera files nothing will send. */
    fun deleteOlderThan(
        maxAgeMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        for (root in listOf(uploads, camera)) {
            root.listFiles()?.forEach { entry ->
                if (nowMillis - entry.lastModified() > maxAgeMillis) entry.deleteRecursively()
            }
        }
    }

    /** Whether this file is somewhere below [directory]. */
    private fun File.isUnder(directory: File): Boolean {
        val root = directory.canonicalOrNull() ?: return false
        return canonicalOrNull()?.startsWith(root.path + File.separator) == true
    }

    /** Whether this file is an entry of [directory] itself. */
    private fun File.isDirectlyIn(directory: File): Boolean {
        val root = directory.canonicalOrNull() ?: return false
        return canonicalOrNull()?.parentFile == root
    }

    private fun File.canonicalOrNull(): File? =
        try {
            canonicalFile
        } catch (_: IOException) {
            null
        }

    private companion object {
        const val UPLOADS = "uploads"
        const val CAMERA = "camera"
    }
}
