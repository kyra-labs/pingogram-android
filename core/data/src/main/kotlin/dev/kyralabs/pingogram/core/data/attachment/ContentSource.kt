package dev.kyralabs.pingogram.core.data.attachment

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.OpenableColumns
import java.io.InputStream

/**
 * What the pickers return, read through Android's `ContentResolver`: a seam, so preparing files and contacts runs in
 * JVM tests with a fake. Blocking IO, off the main thread. Each URI is readable only while the read grant the picker
 * gave with it lasts.
 */
internal interface ContentSource {
    /** The name, size and type of the content at [uri], each null when the provider does not tell. */
    fun describe(uri: String): ContentInfo

    /** The content at [uri]; null when it cannot be opened. The caller closes it. */
    fun open(uri: String): InputStream?

    /** The name and phone number of the contact's phone number at [uri]; null when it cannot be read. */
    fun phoneContact(uri: String): PhoneContact?
}

/** What a provider tells about a picked file. `toString()` leaves the name out. */
internal data class ContentInfo(
    val displayName: String?,
    val size: Long?,
    val mimeType: String?,
) {
    override fun toString() = "ContentInfo(size=$size, mimeType=$mimeType, …)"
}

/** A phone number from the contacts and the name of its contact. `toString()` leaves both out. */
internal data class PhoneContact(
    val displayName: String,
    val phoneNumber: String,
) {
    override fun toString() = "PhoneContact(…)"
}

/** [ContentSource] on [resolver]. */
internal class AndroidContentSource(
    private val resolver: ContentResolver,
) : ContentSource {
    override fun describe(uri: String): ContentInfo {
        val parsed = Uri.parse(uri)
        var name: String? = null
        var size: Long? = null
        orNullOnFailure {
            resolver.query(parsed, OPENABLE_COLUMNS, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        val mimeType = orNullOnFailure { resolver.getType(parsed) }
        return ContentInfo(name, size, mimeType)
    }

    override fun open(uri: String): InputStream? = orNullOnFailure { resolver.openInputStream(Uri.parse(uri)) }

    override fun phoneContact(uri: String): PhoneContact? =
        orNullOnFailure {
            resolver.query(Uri.parse(uri), PHONE_COLUMNS, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val number = cursor.getString(cursor.getColumnIndexOrThrow(Phone.NUMBER))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME))
                number?.let { PhoneContact(name.orEmpty(), it) }
            }
        }

    /**
     * [block], or null when the provider refuses: the grant ended, the file is gone, or the provider fails in its own
     * way (providers of other apps throw what they like).
     */
    @Suppress("TooGenericExceptionCaught") // Any failure of another app's provider means the content cannot be read.
    private inline fun <T> orNullOnFailure(block: () -> T?): T? =
        try {
            block()
        } catch (_: Exception) {
            null
        }

    private companion object {
        val OPENABLE_COLUMNS = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val PHONE_COLUMNS = arrayOf(Phone.NUMBER, Phone.DISPLAY_NAME)
    }
}
