package dev.kyralabs.pingogram.core.data.backup

import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.AccountBackup
import dev.kyralabs.pingogram.core.domain.model.AppInfo
import dev.kyralabs.pingogram.core.domain.model.Backup
import dev.kyralabs.pingogram.core.domain.model.BackupHeader
import dev.kyralabs.pingogram.core.domain.model.BackupOpening
import dev.kyralabs.pingogram.core.domain.model.BackupRestored
import dev.kyralabs.pingogram.core.domain.model.BackupSection
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.AccountRepositories
import dev.kyralabs.pingogram.core.domain.repository.BackupRepository
import dev.kyralabs.pingogram.core.domain.repository.LockedChatIdsRepository
import dev.kyralabs.pingogram.core.domain.repository.SettingsBackupRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BackupRepository] in [BackupFile]s: a body of the `settings` section ([SettingsBackupRepository]) and an `account`
 * section for each signed-in account of [accounts] (never the demo, a graph of its own), by its Telegram user id:
 * bookmarks with their tags, reminders, keyword hits and snippets, without those of locked chats and of secret chats
 * (or of chats TDLib cannot tell, to be safe).
 *
 * Restoring merges into the account with the same user id only ([AccountRestore]); the body's JSON is read and written
 * on the [io] dispatcher, as are the files.
 */
@Singleton
class LocalBackupRepository
    @Inject
    constructor(
        private val settings: SettingsBackupRepository,
        private val accounts: AccountGraphs,
        private val lockedChats: LockedChatIdsRepository,
        private val appInfo: AppInfo,
        private val clock: WallClock,
        @param:Dispatcher(IO) private val io: CoroutineDispatcher,
    ) : BackupRepository {
        override suspend fun write(
            output: OutputStream,
            password: String,
        ): Outcome<BackupHeader> = withContext(io) { write(output, BackupKey.of(password)) }

        /** Writes a backup into [output] with [key], made from a password: automatic backups' kept one. */
        internal suspend fun write(
            output: OutputStream,
            key: BackupKey,
        ): Outcome<BackupHeader> =
            withContext(io) {
                val sections = accounts.accounts.first().mapNotNull { backupOf(it.repositories) }
                val header =
                    BackupHeader(
                        formatVersion = BackupFile.FORMAT_VERSION,
                        appVersion = appInfo.versionName,
                        createdAt = Instant.ofEpochSecond(clock.now().epochSecond),
                        sections =
                            setOfNotNull(
                                BackupSection.Settings,
                                BackupSection.Account.takeIf { sections.any() },
                            ),
                    )
                val body =
                    JSONObject()
                        .put(SETTINGS, settings.backedUpSettings().toSettingsJson())
                        .put(ACCOUNTS, JSONArray(sections.map { it.toJson() }))
                try {
                    BackupFile.write(output, header, key, body.toString())
                    Outcome.Success(header)
                } catch (error: IOException) {
                    Outcome.Failure(DomainError.Unknown(code = 0, message = "Backup not written: ${error.message}"))
                }
            }

        override suspend fun header(input: InputStream): BackupHeader? =
            withContext(io) {
                try {
                    BackupFile.readHeader(input)
                } catch (_: IOException) {
                    null
                }
            }

        override suspend fun open(
            input: InputStream,
            password: String,
        ): BackupOpening =
            withContext(io) {
                try {
                    when (val read = BackupFile.read(input, password)) {
                        is BackupFileRead.Read -> BackupOpening.Opened(backupOf(read))
                        is BackupFileRead.Newer -> BackupOpening.NewerVersion(read.header)
                        BackupFileRead.WrongPassword -> BackupOpening.WrongPassword
                        BackupFileRead.NotABackup -> BackupOpening.NotABackup
                    }
                } catch (_: JSONException) {
                    BackupOpening.NotABackup
                } catch (_: IOException) {
                    BackupOpening.NotABackup
                }
            }

        override suspend fun restore(backup: Backup): Outcome<BackupRestored> {
            val settingsRestored = backup.settings?.let { settings.restoreSettings(it) } ?: Outcome.Success(Unit)
            val byUser =
                accounts.accounts.first().associateBy {
                    it.repositories
                        .userRepository()
                        .myUserId.value
                }
            val onPhone = backup.accounts.filter { it.userId in byUser }
            return settingsRestored
                .then {
                    onPhone.firstFailure { section ->
                        AccountRestore(byUser.getValue(section.userId).repositories, clock.now()).restore(section)
                    }
                }.map {
                    BackupRestored(
                        settings = backup.settings != null,
                        accounts = onPhone.map { it.userId },
                        otherAccounts = backup.accounts.map { it.userId } - onPhone.map { it.userId }.toSet(),
                    )
                }
        }

        /** The account section of [repositories]' account; null before it signed in. */
        private suspend fun backupOf(repositories: AccountRepositories): AccountBackup? {
            val userId = repositories.userRepository().myUserId.value ?: return null
            val locked = lockedChats.ids.first()
            val secret = mutableMapOf<Long, Boolean>()
            val chats = repositories.chatRepository()

            suspend fun kept(chatId: Long): Boolean =
                chatId !in locked &&
                    !secret.getOrPut(chatId) {
                        when (val chat = chats.getChat(chatId)) {
                            is Outcome.Success -> chat.value.type is ChatType.Secret
                            is Outcome.Failure -> true
                        }
                    }
            val bookmarks = repositories.bookmarkRepository()
            return AccountBackup(
                userId = userId,
                bookmarks = bookmarks.bookmarks.first().filter { kept(it.chatId) },
                tags = bookmarks.tags.first(),
                reminders =
                    repositories
                        .reminderRepository()
                        .reminders
                        .first()
                        .filter { kept(it.chatId) },
                keywordHits =
                    repositories
                        .keywordHitRepository()
                        .recent
                        .first()
                        .filter { kept(it.chatId) },
                snippets = repositories.snippetRepository().snippets.first(),
                // A private chat's id is its user's: a locked chat's contact keeps their reminder off the file.
                birthdayReminders =
                    repositories
                        .birthdayReminderRepository()
                        .reminders
                        .first()
                        .filter { it.userId !in locked },
            )
        }

        private fun backupOf(read: BackupFileRead.Read): Backup {
            val body = JSONObject(read.body)
            val sections = body.optJSONArray(ACCOUNTS) ?: JSONArray()
            return Backup(
                header = read.header,
                settings = body.optJSONObject(SETTINGS)?.toSettings(),
                accounts = (0 until sections.length()).map { sections.getJSONObject(it).toAccountBackup() },
            )
        }

        private companion object {
            const val SETTINGS = "settings"
            const val ACCOUNTS = "accounts"
        }
    }
