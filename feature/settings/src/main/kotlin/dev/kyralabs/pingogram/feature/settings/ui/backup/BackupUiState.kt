package dev.kyralabs.pingogram.feature.settings.ui.backup

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.BackupFileInfo
import kotlinx.collections.immutable.ImmutableList
import java.time.Instant

/** What the Backup & restore page shows (`docs/adr/0039-local-backup-and-restore.md`). */
@Immutable
sealed interface BackupUiState {
    data object Loading : BackupUiState

    /**
     * The kept folder's [folderName] (null while none is kept), its newest backup ([lastBackup], null for none),
     * whether a backup is being written ([backingUp]), the password dialog while it asks ([password]), and where a
     * restore is ([restore], null while none is under way).
     */
    data class Content(
        val folderName: String? = null,
        val lastBackup: BackupFileInfo? = null,
        val backingUp: Boolean = false,
        val password: PasswordDialog? = null,
        val restore: RestoreStep? = null,
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

    /** Restores backup [document]: picked with Android's file picker, or named by the chat list's restore card. */
    data class RestoreFile(
        val document: String,
    ) : BackupEvent

    /** The restore's [password], typed. */
    data class OpenBackup(
        val password: String,
    ) : BackupEvent

    /** "Restore", once the backup's summary shows. */
    data object ConfirmRestore : BackupEvent

    data object DismissRestore : BackupEvent
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

    /** A backup was restored; [otherAccount] when it held another account's data, which stayed in it. */
    data class Restored(
        val otherAccount: Boolean,
    ) : BackupEffect

    /** A file that cannot be restored, and why. */
    data class RestoreRefused(
        val refusal: RestoreRefusal,
    ) : BackupEffect
}

/** The shortest backup password the dialog takes. */
internal const val MIN_PASSWORD_LENGTH = 6

/**
 * Where restoring a backup is: its [Password] (after a [Password.wrongPassword], while [Password.opening]), then
 * what it holds ([Summary]), while [Summary.restoring].
 */
@Immutable
sealed interface RestoreStep {
    data class Password(
        val wrongPassword: Boolean = false,
        val opening: Boolean = false,
    ) : RestoreStep

    /** The backup made at [createdAt]: whether it holds [settings], and each account's data ([accounts]). */
    data class Summary(
        val createdAt: Instant,
        val settings: Boolean,
        val accounts: ImmutableList<AccountSummary>,
        val restoring: Boolean = false,
    ) : RestoreStep
}

/**
 * An account's data in a backup: the account's [name] when it is signed in on this phone (its data is restored into
 * it), null for another account (whose data stays in the backup), and how many of each it holds.
 */
@Immutable
data class AccountSummary(
    val name: String?,
    val bookmarks: Int,
    val reminders: Int,
    val keywordHits: Int,
    val snippets: Int,
)

/** Why a backup cannot be restored: made by a newer Pingogram, or not a backup (or a damaged one). */
enum class RestoreRefusal { NewerVersion, NotABackup }
