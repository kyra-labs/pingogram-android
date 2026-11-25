package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant

/**
 * The header of a backup file (`docs/adr/0039-local-backup-and-restore.md`), which reads without the backup's password:
 * the [formatVersion] it is written in, the [appVersion] that wrote it, when it was made ([createdAt]) and which
 * [sections] it holds.
 */
data class BackupHeader(
    val formatVersion: Int,
    val appVersion: String,
    val createdAt: Instant,
    val sections: ImmutableSet<BackupSection>,
)

/**
 * A part of a backup: the app's settings, or each account's bookmarks, reminders, birthday reminders, keyword hits,
 * snippets and notes on chats.
 */
enum class BackupSection {
    Settings,
    Account,
}

/**
 * A backup opened with its password (`docs/adr/0039-local-backup-and-restore.md`): its [header], the app's [settings]
 * by key (null when it holds none; see `SettingsBackupRepository`) and each account's data ([accounts]).
 */
class Backup(
    val header: BackupHeader,
    val settings: ImmutableMap<String, Any>?,
    val accounts: ImmutableList<AccountBackup>,
) {
    override fun toString() = "Backup(header=$header, settings=${settings?.size}, accounts=$accounts)"
}

/**
 * What a backup keeps of the account of Telegram user [userId]: its [bookmarks] with their [tags], its [reminders],
 * [keywordHits], [snippets], [birthdayReminders] and [chatNotes], none of a secret chat or a locked chat. `toString()`
 * counts them only.
 */
data class AccountBackup(
    val userId: Long,
    val bookmarks: ImmutableList<Bookmark> = persistentListOf(),
    val tags: ImmutableList<BookmarkTag> = persistentListOf(),
    val reminders: ImmutableList<MessageReminder> = persistentListOf(),
    val keywordHits: ImmutableList<KeywordHit> = persistentListOf(),
    val snippets: ImmutableList<Snippet> = persistentListOf(),
    val birthdayReminders: ImmutableList<BirthdayReminder> = persistentListOf(),
    val chatNotes: ImmutableList<ChatNote> = persistentListOf(),
) {
    override fun toString() =
        "AccountBackup(bookmarks=${bookmarks.size}, tags=${tags.size}, reminders=${reminders.size}, " +
            "keywordHits=${keywordHits.size}, snippets=${snippets.size}, " +
            "birthdayReminders=${birthdayReminders.size}, chatNotes=${chatNotes.size})"
}

/** What opening a backup file with a password found. */
sealed interface BackupOpening {
    /** The [backup], ready to restore. */
    data class Opened(
        val backup: Backup,
    ) : BackupOpening

    /** A backup of a newer format than this app reads ([header]): the app must be updated first. */
    data class NewerVersion(
        val header: BackupHeader,
    ) : BackupOpening

    /** The password is not the backup's, or the file was changed since it was written. */
    data object WrongPassword : BackupOpening

    /** Not a backup file, or a damaged one. */
    data object NotABackup : BackupOpening
}

/**
 * What a restore did: whether it replaced the [settings], the user ids of the accounts on the phone it restored into
 * ([accounts]), and those of the backup's accounts that are not on the phone ([otherAccounts]), restored into none.
 */
data class BackupRestored(
    val settings: Boolean,
    val accounts: ImmutableList<Long>,
    val otherAccounts: ImmutableList<Long>,
)
