package dev.kyralabs.pingogram.core.data.backup

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.AccountBackup
import dev.kyralabs.pingogram.core.domain.model.AppInfo
import dev.kyralabs.pingogram.core.domain.model.BackupHeader
import dev.kyralabs.pingogram.core.domain.model.BackupOpening
import dev.kyralabs.pingogram.core.domain.model.BackupSection
import dev.kyralabs.pingogram.core.domain.model.Bookmark
import dev.kyralabs.pingogram.core.domain.model.BookmarkTag
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatPermissions
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.KeywordHit
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
import dev.kyralabs.pingogram.core.domain.model.Snippet
import dev.kyralabs.pingogram.core.domain.repository.AccountGraph
import dev.kyralabs.pingogram.core.domain.repository.AccountRepositories
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.LockedChatIdsRepository
import dev.kyralabs.pingogram.core.domain.repository.SettingsBackupRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.testing.FakeAccountGraphs
import dev.kyralabs.pingogram.core.testing.FakeBookmarkRepository
import dev.kyralabs.pingogram.core.testing.FakeKeywordHitRepository
import dev.kyralabs.pingogram.core.testing.FakeReminderRepository
import dev.kyralabs.pingogram.core.testing.FakeSnippetRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.ZoneId

/**
 * [LocalBackupRepository] on fake accounts: what a backup holds and leaves out, its password and a newer format. On
 * Robolectric for Android's `org.json`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalBackupRepositoryTest {
    /** An account on the phone: its user id, its chats' types, and its data. */
    private class Account(
        userId: Long?,
        val secretChats: Set<Long> = emptySet(),
    ) {
        val userId = MutableStateFlow(userId)
        val bookmarks = FakeBookmarkRepository()
        val reminders = FakeReminderRepository()
        val hits = FakeKeywordHitRepository()
        val snippets = FakeSnippetRepository()
    }

    private val settings = FakeSettings(mapOf("appearance.theme_mode" to "Dark"))
    private val locked = MutableStateFlow(setOf(LOCKED_CHAT))

    private fun repository(vararg accounts: Account) =
        LocalBackupRepository(
            settings = settings,
            accounts =
                FakeAccountGraphs(
                    accounts
                        .mapIndexed { slot, account -> AccountGraph(slot, account.repositories()) }
                        .toPersistentList(),
                ),
            lockedChats = FakeLockedChats(locked),
            appInfo = AppInfo(versionName = "2.4.0"),
            clock = FixedClock,
            io = Dispatchers.Unconfined,
        )

    private suspend fun LocalBackupRepository.backup(password: String = PASSWORD): ByteArray =
        ByteArrayOutputStream().also { write(it, password) }.toByteArray()

    private fun Account.withData(): Account =
        apply {
            bookmarks.savedTags.value = listOf(BookmarkTag(id = 1, name = "Work", color = 2))
            bookmarks.saved.value =
                listOf(
                    Bookmark(CHAT, 10, savedAt = 100, tagIds = persistentListOf(1), note = "read later"),
                    Bookmark(SECRET_CHAT, 11, savedAt = 100),
                    Bookmark(LOCKED_CHAT, 12, savedAt = 100),
                )
            reminders.saved.value = listOf(MessageReminder(CHAT, 10, Instant.ofEpochSecond(PAST)))
            hits.hits.value = listOf(KeywordHit(CHAT, 13, "launch", date = 900), KeywordHit(SECRET_CHAT, 14, "x", 900))
            snippets.saved.value = listOf(Snippet(1, "addr", "1 Main St"))
        }

    @Test
    fun `a backup holds the settings and each account's data, without secret and locked chats`() =
        runTest {
            val account = Account(USER, secretChats = setOf(SECRET_CHAT)).withData()
            val repository = repository(account)

            val opened = repository.open(repository.backup().inputStream(), PASSWORD)

            val backup = (opened as BackupOpening.Opened).backup
            assertEquals(BackupHeader(1, "2.4.0", Instant.ofEpochSecond(NOW), BOTH), backup.header)
            assertEquals(mapOf("appearance.theme_mode" to "Dark"), backup.settings)
            assertEquals(
                listOf(
                    AccountBackup(
                        userId = USER,
                        bookmarks =
                            account.bookmarks.saved.value
                                .take(1),
                        tags = account.bookmarks.savedTags.value,
                        reminders = account.reminders.saved.value,
                        keywordHits =
                            account.hits.hits.value
                                .take(1),
                        snippets = account.snippets.saved.value,
                    ),
                ),
                backup.accounts,
            )
        }

    @Test
    fun `an account that has not signed in yet is not backed up`() =
        runTest {
            val repository = repository(Account(userId = null).withData())

            val backup = (repository.open(repository.backup().inputStream(), PASSWORD) as BackupOpening.Opened).backup

            assertEquals(emptyList<AccountBackup>(), backup.accounts)
            assertEquals(setOf(BackupSection.Settings), backup.header.sections)
        }

    @Test
    fun `another password does not open it`() =
        runTest {
            val repository = repository(Account(USER).withData())

            assertEquals(BackupOpening.WrongPassword, repository.open(repository.backup().inputStream(), "guess"))
        }

    @Test
    fun `a backup of a newer format asks for an update`() =
        runTest {
            val newer = BackupHeader(2, "9.0.0", Instant.ofEpochSecond(NOW), setOf(BackupSection.Settings))
            val salt = BackupCipher.newSalt()
            val file =
                ByteArrayOutputStream().also {
                    BackupFile.write(it, newer, BackupKey(BackupCipher.key(PASSWORD, salt, 1), salt, 1), "{}")
                }

            val opened = repository().open(file.toByteArray().inputStream(), PASSWORD)

            assertEquals(BackupOpening.NewerVersion(newer), opened)
        }

    /** The account's repositories that a backup uses; every other one fails the test. */
    private fun Account.repositories(): AccountRepositories =
        proxy { name, _ ->
            when (name) {
                "userRepository" -> {
                    proxy<UserRepository> { method, _ -> userId.takeIf { method == "getMyUserId" } }
                }

                "chatRepository" -> {
                    proxy<ChatRepository> { method, args ->
                        (args?.first() as? Long)?.takeIf { method == "getChat" }?.let { id ->
                            val type = if (id in secretChats) ChatType.Secret(1, 2) else ChatType.Private(2)
                            Outcome.Success(chat(id, type))
                        }
                    }
                }

                "bookmarkRepository" -> {
                    bookmarks
                }

                "reminderRepository" -> {
                    reminders
                }

                "keywordHitRepository" -> {
                    hits
                }

                "snippetRepository" -> {
                    snippets
                }

                else -> {
                    null
                }
            }
        }

    private inline fun <reified T : Any> proxy(crossinline answer: (String, Array<Any?>?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { self, method, args ->
            when (method.name) {
                "equals" -> self === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(self)
                "toString" -> T::class.java.simpleName
                else -> answer(method.name, args) ?: error("Not used: ${method.name}")
            }
        } as T

    private fun chat(
        id: Long,
        type: ChatType,
    ) = Chat(
        id = id,
        type = type,
        title = "",
        photo = null,
        lastMessage = null,
        positions = persistentListOf(),
        unreadCount = 0,
        unreadMentionCount = 0,
        unreadReactionCount = 0,
        lastReadInboxMessageId = 0,
        lastReadOutboxMessageId = 0,
        isMarkedAsUnread = false,
        isMuted = false,
        draft = null,
        hasProtectedContent = false,
        messageAutoDeleteTime = 0,
        permissions = ChatPermissions(canSendBasicMessages = true),
    )

    private class FakeSettings(
        var values: Map<String, Any>,
    ) : SettingsBackupRepository {
        var restored: Map<String, Any>? = null

        override suspend fun backedUpSettings(): Map<String, Any> = values

        override suspend fun restoreSettings(settings: Map<String, Any>): Outcome<Unit> {
            restored = settings
            return Outcome.Success(Unit)
        }
    }

    private class FakeLockedChats(
        override val ids: MutableStateFlow<Set<Long>>,
    ) : LockedChatIdsRepository {
        override suspend fun setIds(ids: Set<Long>): Outcome<Unit> = Outcome.Failure(DomainError.Unknown(0, "unused"))
    }

    private object FixedClock : WallClock {
        override fun now(): Instant = Instant.ofEpochSecond(NOW, 500)

        override fun zone(): ZoneId = ZoneId.of("UTC")

        override val minutes: SharedFlow<Instant> = MutableSharedFlow()
    }

    private companion object {
        const val PASSWORD = "correct horse"
        const val USER = 7L
        const val CHAT = 100L
        const val SECRET_CHAT = 200L
        const val LOCKED_CHAT = 300L
        const val NOW = 1_000L
        const val PAST = 500L
        val BOTH = setOf(BackupSection.Settings, BackupSection.Account)
    }
}
