package dev.kyralabs.pingogram.core.data.backup

import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BackupFileInfo
import dev.kyralabs.pingogram.core.domain.model.BackupFrequency
import dev.kyralabs.pingogram.core.domain.model.BackupHeader
import dev.kyralabs.pingogram.core.domain.repository.AutomaticBackupRepository
import dev.kyralabs.pingogram.core.domain.repository.BackupSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AutomaticBackupRepository] with the key [keys] keeps (from the last backup the user wrote with a password): a dated
 * backup into the kept folder as the user's are ([folders]), then the folder's backups but the newest [KEPT] deleted.
 */
@Singleton
class KeystoreAutomaticBackupRepository internal constructor(
    private val settings: BackupSettingsRepository,
    private val keys: BackupKeyStore,
    private val folders: DocumentBackupFolderRepository,
    private val write: suspend (OutputStream, BackupKey) -> Outcome<BackupHeader>,
    private val io: CoroutineDispatcher,
) : AutomaticBackupRepository {
    @Inject
    constructor(
        settings: BackupSettingsRepository,
        keys: BackupKeyStore,
        folders: DocumentBackupFolderRepository,
        backups: LocalBackupRepository,
        @Dispatcher(IO) io: CoroutineDispatcher,
    ) : this(settings, keys, folders, { output, key -> backups.write(output, key) }, io)

    override val frequency: Flow<BackupFrequency> =
        combine(settings.folderUri, settings.frequency) { folder, chosen ->
            if (folder == null) BackupFrequency.Off else chosen ?: BackupFrequency.Weekly
        }.distinctUntilChanged()

    override val isReady: Flow<Boolean> =
        combine(settings.folderUri, keys.hasKey.flowOn(io)) { folder, hasKey -> folder != null && hasKey }
            .distinctUntilChanged()

    override suspend fun setFrequency(frequency: BackupFrequency): Outcome<Unit> = settings.setFrequency(frequency)

    override suspend fun backUp(): Outcome<BackupFileInfo> {
        val key = withContext(io) { keys.key() } ?: return Outcome.Failure(DomainError.FileUnavailable)
        return folders.backUp { write(it, key) }.also { if (it is Outcome.Success) folders.prune(KEPT) }
    }

    private companion object {
        /** How many backups the folder keeps (owner decision 2026-11-17). */
        const val KEPT = 3
    }
}
