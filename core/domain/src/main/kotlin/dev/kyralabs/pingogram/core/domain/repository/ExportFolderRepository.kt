package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome

/**
 * The folders exports go to (`REQUIREMENTS.md` §5.9, `docs/adr/0021-chat-export.md`): picked by the user through the
 * Storage Access Framework, with no storage permission. A folder's grant is kept from when it is picked until the
 * export into it ends ([release]), so an export continues after process death.
 */
interface ExportFolderRepository {
    /**
     * Keeps the write grant to folder [treeUri], just picked (a tree URI), and reads its name; fails with
     * `DomainError.FileUnavailable` if the folder cannot be used.
     */
    suspend fun keep(treeUri: String): Outcome<String>

    /** Gives back the grant to folder [treeUri]; nothing happens without one. */
    suspend fun release(treeUri: String)
}
