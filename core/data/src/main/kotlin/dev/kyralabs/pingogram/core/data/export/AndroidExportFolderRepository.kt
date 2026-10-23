package dev.kyralabs.pingogram.core.data.export

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.ExportFolderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [ExportFolderRepository] on the Storage Access Framework: the folder's grant persisted with
 * `takePersistableUriPermission` (read and write) from when it is picked until its export ends, its name read from
 * its provider (`COLUMN_DISPLAY_NAME`) on the IO dispatcher. No storage permission is involved. Nothing about the
 * folder is logged.
 */
class AndroidExportFolderRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:Dispatcher(IO) private val io: CoroutineDispatcher,
    ) : ExportFolderRepository {
        override suspend fun keep(treeUri: String): Outcome<String> =
            withContext(io) {
                val tree = treeUri.toUri()
                try {
                    context.contentResolver.takePersistableUriPermission(tree, FLAGS)
                    val document =
                        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                    val name =
                        context.contentResolver
                            .query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    name?.let { Outcome.Success(it) } ?: Outcome.Failure(DomainError.FileUnavailable)
                } catch (refused: SecurityException) {
                    Log.w(TAG, "Export folder refused: ${refused.javaClass.simpleName}")
                    Outcome.Failure(DomainError.FileUnavailable)
                } catch (invalid: IllegalArgumentException) {
                    Log.w(TAG, "Export folder invalid: ${invalid.javaClass.simpleName}")
                    Outcome.Failure(DomainError.FileUnavailable)
                }
            }

        override suspend fun release(treeUri: String) {
            withContext(io) {
                val tree = treeUri.toUri()
                val held = context.contentResolver.persistedUriPermissions.any { it.uri == tree }
                if (held) {
                    try {
                        context.contentResolver.releasePersistableUriPermission(tree, FLAGS)
                    } catch (_: SecurityException) {
                        // Taken back already.
                    }
                }
            }
        }

        private companion object {
            const val TAG = "ChatExport"
            const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }
    }
