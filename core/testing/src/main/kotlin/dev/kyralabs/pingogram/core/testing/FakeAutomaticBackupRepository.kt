package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BackupFileInfo
import dev.kyralabs.pingogram.core.domain.model.BackupFrequency
import dev.kyralabs.pingogram.core.domain.repository.AutomaticBackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/** [AutomaticBackupRepository] in memory: tests set [frequency] and [isReady]; [backUps] counts the backups written. */
class FakeAutomaticBackupRepository : AutomaticBackupRepository {
    override val frequency = MutableStateFlow(BackupFrequency.Off)
    override val isReady = MutableStateFlow(false)
    var failure: DomainError? = null
    var backUps = 0
        private set

    override suspend fun setFrequency(frequency: BackupFrequency): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        this.frequency.value = frequency
        return Outcome.Success(Unit)
    }

    override suspend fun backUp(): Outcome<BackupFileInfo> {
        failure?.let { return Outcome.Failure(it) }
        backUps++
        return Outcome.Success(BackupFileInfo("content://tree/backups/$backUps", "backup", Instant.EPOCH, 1))
    }
}
