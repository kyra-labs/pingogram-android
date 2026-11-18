package dev.kyralabs.pingogram.core.data.backup

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * The backup folder and files on the Storage Access Framework (`docs/adr/0039-local-backup-and-restore.md`), by
 * URI: the seam over `DocumentsContract`, which tests replace with files in memory. Every call blocks (the IO
 * dispatcher); failures throw `IOException`, or `SecurityException` for a grant taken back, or
 * `IllegalArgumentException` for a URI that is not one.
 */
interface BackupDocuments {
    /** Keeps the read and write grant to folder [treeUri] for as long as the app is installed. */
    fun keepGrant(treeUri: String)

    /** Gives back the grant to folder [treeUri]; nothing without one. */
    fun releaseGrant(treeUri: String)

    /** Folder [treeUri]'s name; null when it is gone or its grant was taken back. */
    fun folderName(treeUri: String): String?

    /** The files in folder [treeUri]. */
    fun files(treeUri: String): List<BackupDocument>

    /** File [document], in a folder or picked by the user; null when it is gone. */
    fun file(document: String): BackupDocument?

    /** A new, empty file named [name] in folder [treeUri]; the provider may number the name if it is taken. */
    fun create(
        treeUri: String,
        name: String,
    ): String

    fun openInput(document: String): InputStream

    fun openOutput(document: String): OutputStream

    /** Deletes [document]; false if it could not be. */
    fun delete(document: String): Boolean
}

/** Folder [treeUri] as a document. */
private fun folderOf(treeUri: String): Uri {
    val tree = treeUri.toUri()
    return DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
}

/** A file: its [document] URI, [name], when it was last written ([modifiedMillis], 0 when unknown), its [size]. */
data class BackupDocument(
    val document: String,
    val name: String,
    val modifiedMillis: Long,
    val size: Long,
)

/** [BackupDocuments] on the platform's `DocumentsContract`, without `androidx.documentfile`. */
class AndroidBackupDocuments
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : BackupDocuments {
        private val resolver: ContentResolver get() = context.contentResolver

        override fun keepGrant(treeUri: String) {
            resolver.takePersistableUriPermission(treeUri.toUri(), GRANT)
        }

        override fun releaseGrant(treeUri: String) {
            val tree = treeUri.toUri()
            if (resolver.persistedUriPermissions.any { it.uri == tree }) {
                resolver.releasePersistableUriPermission(
                    tree,
                    GRANT,
                )
            }
        }

        override fun folderName(treeUri: String): String? =
            query(folderOf(treeUri), Document.COLUMN_DISPLAY_NAME).firstOrNull()?.name

        override fun files(treeUri: String): List<BackupDocument> {
            val tree = treeUri.toUri()
            val children =
                DocumentsContract.buildChildDocumentsUriUsingTree(
                    tree,
                    DocumentsContract.getTreeDocumentId(tree),
                )
            return query(
                children,
                Document.COLUMN_DOCUMENT_ID,
            ) { id -> DocumentsContract.buildDocumentUriUsingTree(tree, id) }
        }

        override fun file(document: String): BackupDocument? = query(document.toUri(), null).firstOrNull()

        override fun create(
            treeUri: String,
            name: String,
        ): String =
            DocumentsContract.createDocument(resolver, folderOf(treeUri), MIME_TYPE, name)?.toString()
                ?: throw FileNotFoundException("The provider created no document")

        override fun openInput(document: String): InputStream =
            resolver.openInputStream(document.toUri()) ?: throw FileNotFoundException("The provider opened no stream")

        override fun openOutput(document: String): OutputStream =
            resolver.openOutputStream(document.toUri(), "wt")
                ?: throw FileNotFoundException("The provider opened no stream")

        override fun delete(document: String): Boolean = DocumentsContract.deleteDocument(resolver, document.toUri())

        /**
         * The documents [uri] lists (one for a file, its children for a folder's children URI): each with its URI, from
         * [idColumn] through [uriOf] when listing children, or [uri] itself.
         */
        private fun query(
            uri: Uri,
            idColumn: String?,
            uriOf: ((String) -> Uri)? = null,
        ): List<BackupDocument> {
            val columns =
                listOfNotNull(
                    idColumn,
                    Document.COLUMN_DISPLAY_NAME,
                    Document.COLUMN_LAST_MODIFIED,
                    OpenableColumns.SIZE,
                )
            return resolver
                .query(uri, columns.distinct().toTypedArray(), null, null, null)
                ?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val name =
                                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)) ?: continue
                            val id =
                                idColumn?.takeIf { uriOf != null }?.let {
                                    cursor.getString(
                                        cursor.getColumnIndexOrThrow(it),
                                    )
                                }
                            val modified = cursor.getColumnIndex(Document.COLUMN_LAST_MODIFIED)
                            val size = cursor.getColumnIndex(OpenableColumns.SIZE)
                            add(
                                BackupDocument(
                                    document = (id?.let { uriOf?.invoke(it) } ?: uri).toString(),
                                    name = name,
                                    modifiedMillis =
                                        if (modified >= 0 &&
                                            !cursor.isNull(modified)
                                        ) {
                                            cursor.getLong(modified)
                                        } else {
                                            0
                                        },
                                    size = if (size >= 0 && !cursor.isNull(size)) cursor.getLong(size) else 0,
                                ),
                            )
                        }
                    }
                }.orEmpty()
        }

        private companion object {
            const val GRANT = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            /** A backup is no type Android knows: any app may hold it, and the picker shows it. */
            const val MIME_TYPE = "application/octet-stream"
        }
    }
