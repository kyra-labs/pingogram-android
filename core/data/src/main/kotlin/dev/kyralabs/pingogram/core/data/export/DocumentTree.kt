package dev.kyralabs.pingogram.core.data.export

import android.content.ContentResolver
import android.content.Context
import android.provider.DocumentsContract
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

/**
 * The folder an export writes into, and what it creates there, by document URI (`docs/adr/0021-chat-export.md`): the
 * seam over the Storage Access Framework's `DocumentsContract`, which tests replace with a folder in memory. Every
 * call blocks; the exporter calls them on the IO dispatcher. Failures throw [IOException] (a full storage among them,
 * [isStorageFull]) or [SecurityException] (a grant taken back).
 */
interface DocumentTree {
    /** The folder the user picked, tree URI [treeUri], as a document; null if it is gone. */
    fun root(treeUri: String): String?

    /** A new folder named [name] in folder [parent]; the provider may number the name if it is taken. */
    fun createFolder(
        parent: String,
        name: String,
    ): String

    /** A new, empty file named [name] of [mimeType] in folder [parent]; the provider may number the name. */
    fun createFile(
        parent: String,
        name: String,
        mimeType: String,
    ): String

    /** Writes [document] from its start; the caller closes the stream. */
    fun openOutput(document: String): OutputStream

    /** Deletes [document], a folder with everything in it; false if it could not be. */
    fun delete(document: String): Boolean
}

/** Whether this failure is a full storage (`ENOSPC`), which the export page says in its own words. */
internal fun IOException.isStorageFull(): Boolean {
    val text = generateSequence<Throwable>(this) { it.cause }.mapNotNull { it.message }.joinToString(" ")
    return "ENOSPC" in text || "No space left" in text
}

/** [DocumentTree] on the platform's `DocumentsContract`, without `androidx.documentfile`, which adds nothing needed. */
class AndroidDocumentTree
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : DocumentTree {
        private val resolver: ContentResolver get() = context.contentResolver

        override fun root(treeUri: String): String? {
            val tree = treeUri.toUri()
            val document =
                try {
                    DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                } catch (_: IllegalArgumentException) {
                    return null
                }
            val exists =
                resolver
                    .query(document, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                    ?.use { it.moveToFirst() } == true
            return document.toString().takeIf { exists }
        }

        override fun createFolder(
            parent: String,
            name: String,
        ): String = create(parent, DocumentsContract.Document.MIME_TYPE_DIR, name)

        override fun createFile(
            parent: String,
            name: String,
            mimeType: String,
        ): String = create(parent, mimeType, name)

        private fun create(
            parent: String,
            mimeType: String,
            name: String,
        ): String =
            DocumentsContract.createDocument(resolver, parent.toUri(), mimeType, name)?.toString()
                ?: throw FileNotFoundException("The provider created no document")

        override fun openOutput(document: String): OutputStream =
            resolver.openOutputStream(document.toUri(), "wt")
                ?: throw FileNotFoundException("The provider opened no stream")

        override fun delete(document: String): Boolean =
            try {
                DocumentsContract.deleteDocument(resolver, document.toUri())
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            } catch (_: IllegalArgumentException) {
                false
            }
    }
