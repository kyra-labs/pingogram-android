package dev.kyralabs.pingogram.core.ui.file

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

// The files the camera app writes photos into for the composer's attach menu (REQUIREMENTS.md FR-C5): `core:data`'s
// UploadFiles creates them in cacheDir/camera, and the camera app gets a write grant for one of them at a time through
// the app's FileProvider, which serves that directory as `cache-path` "camera" (:app's res/xml/file_paths.xml).

/** The directory under `cacheDir` of the camera's files; keep in sync with `file_paths.xml` and `UploadFiles`. */
private const val CAMERA_DIRECTORY = "camera"

/**
 * A `content://` URI through the app's `FileProvider` for the camera's file at [path], to hand to the camera app
 * (`ActivityResultContracts.TakePicture`); null unless it is a file of the camera directory, after resolving `..` and
 * symbolic links.
 */
fun Context.cameraFileUri(path: String): Uri? {
    val root = canonicalOrNull(File(cacheDir, CAMERA_DIRECTORY)) ?: return null
    return canonicalOrNull(File(path))
        ?.takeIf { it.parentFile == root }
        ?.let { file ->
            try {
                FileProvider.getUriForFile(this, tdlibFilesAuthority(), file)
            } catch (_: IllegalArgumentException) {
                // file_paths.xml does not serve the directory.
                null
            }
        }
}

private fun canonicalOrNull(file: File): File? =
    try {
        file.canonicalFile
    } catch (_: IOException) {
        null
    }
