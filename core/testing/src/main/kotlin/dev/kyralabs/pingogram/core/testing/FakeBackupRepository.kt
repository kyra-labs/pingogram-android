package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Backup
import dev.kyralabs.pingogram.core.domain.model.BackupHeader
import dev.kyralabs.pingogram.core.domain.model.BackupOpening
import dev.kyralabs.pingogram.core.domain.model.BackupRestored
import dev.kyralabs.pingogram.core.domain.repository.BackupRepository
import kotlinx.collections.immutable.persistentListOf
import java.io.InputStream
import java.io.OutputStream

/**
 * [BackupRepository] for tests: [restored] are the backups restored, in order; [restoring] what restoring answers, or
 * [failure]. Writing and opening files answer [failure] or nothing useful: `FakeBackupFolderRepository` covers files.
 */
class FakeBackupRepository : BackupRepository {
    val restored = mutableListOf<Backup>()
    var restoring = BackupRestored(settings = true, accounts = persistentListOf(), otherAccounts = persistentListOf())
    var failure: DomainError? = null

    override suspend fun write(
        output: OutputStream,
        password: String,
    ): Outcome<BackupHeader> = Outcome.Failure(failure ?: DomainError.FileUnavailable)

    override suspend fun header(input: InputStream): BackupHeader? = null

    override suspend fun open(
        input: InputStream,
        password: String,
    ): BackupOpening = BackupOpening.NotABackup

    override suspend fun restore(backup: Backup): Outcome<BackupRestored> {
        failure?.let { return Outcome.Failure(it) }
        restored += backup
        return Outcome.Success(restoring)
    }
}
