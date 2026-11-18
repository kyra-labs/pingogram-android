package dev.kyralabs.pingogram.feature.settings.ui.backup

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.BackupFileInfo

/** What the Backup & restore page shows (`docs/adr/0039-local-backup-and-restore.md`). */
@Immutable
sealed interface BackupUiState {
    data object Loading : BackupUiState

    /**
     * The kept folder's [folderName] (null while none is kept), its newest backup ([lastBackup], null for none),
     * whether a backup is being written ([backingUp]), and the password dialog while it asks ([password]).
     */
    data class Content(
        val folderName: String? = null,
        val lastBackup: BackupFileInfo? = null,
        val backingUp: Boolean = false,
        val password: PasswordDialog? = null,
    ) : BackupUiState {
        /** "Back up now" works once a folder is kept and while nothing is being written. */
        val canBackUp: Boolean get() = folderName != null && !backingUp
    }
}

/**
 * The backup password's dialog: [New] at the folder's first backup, which sets the password (typed twice); [Existing]
 * after, which asks for it once. The password is never kept.
 */
enum class PasswordDialog { New, Existing }

/** What the user does on the page, handled by [BackupViewModel.onEvent]. */
sealed interface BackupEvent {
    /** Android's folder picker answered with folder [treeUri]. */
    data class FolderPicked(
        val treeUri: String,
    ) : BackupEvent

    /** "Back up now": the password dialog. */
    data object BackUp : BackupEvent

    /** The dialog's "Back up", with the [password] typed. */
    data class ConfirmPassword(
        val password: String,
    ) : BackupEvent

    data object DismissPassword : BackupEvent
}

/** One-off things the page shows. */
sealed interface BackupEffect {
    /** A backup was written into folder [folderName]. */
    data class BackedUp(
        val folderName: String,
    ) : BackupEffect

    data class ShowError(
        val error: DomainError,
    ) : BackupEffect
}

/** The shortest backup password the dialog takes. */
internal const val MIN_PASSWORD_LENGTH = 6
