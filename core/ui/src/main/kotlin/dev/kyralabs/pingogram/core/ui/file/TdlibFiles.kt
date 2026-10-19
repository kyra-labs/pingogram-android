package dev.kyralabs.pingogram.core.ui.file

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.Locale

// TDLib's downloaded files handed to other apps (REQUIREMENTS.md FR-C6, FR-C10; §6.2): shared from the media viewer,
// documents opened from the conversation. Only through the app's FileProvider, which :app declares with the authority
// "${applicationId}.files" and res/xml/file_paths.xml: it serves TDLib's files directories and nothing else, never
// TDLib's database. Paths outside them are refused here before the provider sees them. Each file goes to the app the
// user picks with a read grant for its URI alone, which ends with that app's task.

/**
 * The directories under `filesDir` whose files may be handed out: TDLib's files directories, of the production and of
 * the test data center (`core:tdlib`'s `TdlibDirectories.files`). Keep in sync with `:app`'s `res/xml/file_paths.xml`.
 */
private val SHAREABLE_DIRECTORIES = listOf("tdlib/files", "tdlib-test/files")

/** The authority of the app's `FileProvider`: `${applicationId}.files`, the package name at run time. */
fun Context.tdlibFilesAuthority(): String = "$packageName.files"

/**
 * A `content://` URI for the file at [path] through the app's `FileProvider`, which other apps can read once granted;
 * null unless it is a file in one of TDLib's files directories, after resolving `..` and symbolic links.
 */
fun Context.tdlibFileUri(path: String): Uri? {
    val file = canonicalFile(path)?.takeIf { it.isFile && isShareable(it) } ?: return null
    return try {
        FileProvider.getUriForFile(this, tdlibFilesAuthority(), file)
    } catch (_: IllegalArgumentException) {
        // A path the provider does not serve: file_paths.xml and SHAREABLE_DIRECTORIES disagree.
        null
    }
}

/** The file at [path] with `..` and symbolic links resolved; null if that fails. */
private fun canonicalFile(path: String): File? =
    try {
        File(path).canonicalFile
    } catch (_: IOException) {
        null
    }

/** Whether the canonical [file] is inside one of TDLib's files directories. */
private fun Context.isShareable(file: File): Boolean =
    SHAREABLE_DIRECTORIES.any { directory ->
        val root = canonicalFile(File(filesDir, directory).path)
        root != null && file.path.startsWith(root.path + File.separator)
    }

/** The MIME type the platform knows for the extension of [path], e.g. `image/jpeg` for `.jpg`; null if none. */
fun mimeTypeOfFile(path: String): String? {
    val extension = path.substringAfterLast('/').substringAfterLast('.', missingDelimiterValue = "")
    if (extension.isEmpty()) return null
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase(Locale.ROOT))
}

/**
 * Shares the TDLib file at [path] through the system's share sheet ([Intent.ACTION_SEND] in a chooser titled
 * [chooserTitle]), as the MIME type its extension names, else [fallbackMimeType]; the receiving app gets a read grant
 * for its URI.
 */
fun Context.shareTdlibFile(
    path: String,
    fallbackMimeType: String,
    chooserTitle: CharSequence,
): ExternalFileResult {
    val uri = tdlibFileUri(path) ?: return ExternalFileResult.Unavailable
    val send =
        Intent(Intent.ACTION_SEND)
            .setType(mimeTypeOfFile(path) ?: fallbackMimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The chooser passes the grant on through the clip data.
    send.clipData = ClipData.newRawUri(null, uri)
    return start(Intent.createChooser(send, chooserTitle))
}

/**
 * Opens the TDLib file at [path] in another app ([Intent.ACTION_VIEW]) with a read grant for its URI. Its type is
 * [mimeType] when that names one (not `application/octet-stream`), else the one its extension names; without either,
 * any app that opens files may take it, in a chooser titled [chooserTitle].
 */
fun Context.viewTdlibFile(
    path: String,
    mimeType: String?,
    chooserTitle: CharSequence,
): ExternalFileResult {
    val uri = tdlibFileUri(path) ?: return ExternalFileResult.Unavailable
    val type = mimeType?.takeIf { it.isNotBlank() && it != GENERIC_MIME_TYPE } ?: mimeTypeOfFile(path)
    val view =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, type ?: ANY_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return start(if (type == null) Intent.createChooser(view, chooserTitle) else view)
}

private fun Context.start(intent: Intent): ExternalFileResult =
    try {
        startActivity(intent)
        ExternalFileResult.Started
    } catch (_: ActivityNotFoundException) {
        ExternalFileResult.NoApp
    }

private const val GENERIC_MIME_TYPE = "application/octet-stream"
private const val ANY_MIME_TYPE = "*/*"
