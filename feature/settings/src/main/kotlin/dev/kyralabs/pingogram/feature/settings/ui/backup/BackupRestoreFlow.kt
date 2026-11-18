package dev.kyralabs.pingogram.feature.settings.ui.backup

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Backup
import dev.kyralabs.pingogram.core.domain.model.BackupOpening
import dev.kyralabs.pingogram.core.domain.repository.BackupFolderRepository
import dev.kyralabs.pingogram.core.domain.repository.BackupRepository
import dev.kyralabs.pingogram.core.domain.repository.DeviceAccountsRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Restoring a backup (`docs/adr/0039-local-backup-and-restore.md`, "Restoring") for [BackupViewModel]: the file the
 * user picked or the chat list's card named, its password, what it holds ([RestoreStep.Summary]: the settings, and each
 * account's data, marked for an account on this phone by its name), then the restore. The opened backup stays here,
 * out of the UI state; a wrong password asks again, a newer or damaged file closes the flow and says why.
 */
internal class BackupRestoreFlow(
    private val folders: BackupFolderRepository,
    private val backups: BackupRepository,
    private val accounts: DeviceAccountsRepository,
    private val scope: CoroutineScope,
    private val onEffect: suspend (BackupEffect) -> Unit,
) {
    private val mutableStep = MutableStateFlow<RestoreStep?>(null)

    /** Where the restore is; null while none is under way. */
    val step: StateFlow<RestoreStep?> = mutableStep.asStateFlow()

    private var document: String? = null
    private var opened: Backup? = null

    /** Starts restoring backup [document]: its password. */
    fun start(document: String) {
        this.document = document
        opened = null
        mutableStep.value = RestoreStep.Password()
    }

    /** Opens the backup with [password]. */
    fun open(password: String) {
        val document = document ?: return
        mutableStep.value = RestoreStep.Password(opening = true)
        scope.launch {
            when (val opening = folders.open(document, password)) {
                is BackupOpening.Opened -> {
                    opened = opening.backup
                    mutableStep.value = summaryOf(opening.backup)
                }

                BackupOpening.WrongPassword -> {
                    mutableStep.value = RestoreStep.Password(wrongPassword = true)
                }

                is BackupOpening.NewerVersion -> {
                    close(BackupEffect.RestoreRefused(RestoreRefusal.NewerVersion))
                }

                BackupOpening.NotABackup -> {
                    close(BackupEffect.RestoreRefused(RestoreRefusal.NotABackup))
                }
            }
        }
    }

    /** Restores the opened backup. */
    fun restore() {
        val backup = opened ?: return
        mutableStep.update { (it as? RestoreStep.Summary)?.copy(restoring = true) ?: it }
        scope.launch {
            val effect =
                when (val restored = backups.restore(backup)) {
                    is Outcome.Success -> {
                        BackupEffect.Restored(
                            otherAccount = restored.value.otherAccounts.isNotEmpty(),
                        )
                    }

                    is Outcome.Failure -> {
                        BackupEffect.ShowError(restored.error)
                    }
                }
            close(effect)
        }
    }

    /** Leaves the restore, forgetting the opened backup. */
    fun dismiss() {
        document = null
        opened = null
        mutableStep.value = null
    }

    private suspend fun close(effect: BackupEffect) {
        dismiss()
        onEffect(effect)
    }

    private suspend fun summaryOf(backup: Backup): RestoreStep.Summary {
        val onPhone =
            accounts.accounts
                .first()
                .mapNotNull { it.user }
                .associateBy { it.id }
        return RestoreStep.Summary(
            createdAt = backup.header.createdAt,
            settings = backup.settings != null,
            accounts =
                backup.accounts
                    .map { account ->
                        AccountSummary(
                            name = onPhone[account.userId]?.let { "${it.firstName} ${it.lastName}".trim() },
                            bookmarks = account.bookmarks.size,
                            reminders = account.reminders.size,
                            keywordHits = account.keywordHits.size,
                            snippets = account.snippets.size,
                        )
                    }.toImmutableList(),
        )
    }
}
