package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.BackupSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [BackupSettingsRepository] in memory; [failure] makes the setters fail. */
class FakeBackupSettingsRepository : BackupSettingsRepository {
    override val folderUri = MutableStateFlow<String?>(null)
    override val restoreDismissedFor = MutableStateFlow<String?>(null)
    override val restorePromptDismissed = MutableStateFlow(false)

    /** What the setters fail with; null saves. */
    var failure: DomainError? = null

    override suspend fun setFolderUri(treeUri: String?): Outcome<Unit> = save { folderUri.value = treeUri }

    override suspend fun dismissRestore(document: String): Outcome<Unit> = save { restoreDismissedFor.value = document }

    override suspend fun dismissRestorePrompt(): Outcome<Unit> = save { restorePromptDismissed.value = true }

    private fun save(change: () -> Unit): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        change()
        return Outcome.Success(Unit)
    }
}
