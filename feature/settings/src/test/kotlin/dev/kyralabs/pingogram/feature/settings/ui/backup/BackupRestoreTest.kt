package dev.kyralabs.pingogram.feature.settings.ui.backup

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AccountBackup
import dev.kyralabs.pingogram.core.domain.model.Backup
import dev.kyralabs.pingogram.core.domain.model.BackupHeader
import dev.kyralabs.pingogram.core.domain.model.BackupOpening
import dev.kyralabs.pingogram.core.domain.model.BackupRestored
import dev.kyralabs.pingogram.core.domain.model.BackupSection
import dev.kyralabs.pingogram.core.domain.model.Bookmark
import dev.kyralabs.pingogram.core.domain.model.DeviceAccount
import dev.kyralabs.pingogram.core.domain.model.Snippet
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.testing.FakeAutomaticBackupRepository
import dev.kyralabs.pingogram.core.testing.FakeBackupFolderRepository
import dev.kyralabs.pingogram.core.testing.FakeBackupRepository
import dev.kyralabs.pingogram.core.testing.FakeDeviceAccountsRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Restoring a backup on the Backup & restore page: its password, what it holds (the account on this phone by its
 * name, another account's data marked as staying in the backup), the restore, and what a wrong password, a newer
 * format or a failed restore do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreTest {
    private val folders = FakeBackupFolderRepository()
    private val backups = FakeBackupRepository()
    private val accounts =
        FakeDeviceAccountsRepository(
            accounts =
                persistentListOf(
                    DeviceAccount(slot = 0, user = user(ADA, "Ada"), unreadChats = 0, isActive = true),
                ),
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        folders.opening = BackupOpening.Opened(BACKUP)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): BackupViewModel =
        BackupViewModel(folders, backups, accounts, FakeAutomaticBackupRepository()).also {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
            runCurrent()
        }

    private fun BackupViewModel.restore() = (uiState.value as BackupUiState.Content).restore

    private fun TestScope.send(
        viewModel: BackupViewModel,
        vararg events: BackupEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    private fun TestScope.effects(viewModel: BackupViewModel): List<BackupEffect> =
        mutableListOf<BackupEffect>().also { list ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(list) }
        }

    @Test
    fun `a backup asks for its password, then shows what it holds for which account`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, BackupEvent.RestoreFile(FILE))
            assertEquals(RestoreStep.Password(), viewModel.restore())
            send(viewModel, BackupEvent.OpenBackup("correct horse"))

            assertEquals(
                RestoreStep.Summary(
                    createdAt = CREATED,
                    settings = true,
                    accounts =
                        persistentListOf(
                            AccountSummary("Ada", bookmarks = 1, reminders = 0, keywordHits = 0, snippets = 1),
                            AccountSummary(null, bookmarks = 0, reminders = 0, keywordHits = 0, snippets = 1),
                        ),
                ),
                viewModel.restore(),
            )
        }

    @Test
    fun `a wrong password asks again`() =
        runTest {
            folders.opening = BackupOpening.WrongPassword
            val viewModel = viewModel()

            send(viewModel, BackupEvent.RestoreFile(FILE), BackupEvent.OpenBackup("guess"))

            assertEquals(RestoreStep.Password(wrongPassword = true), viewModel.restore())
        }

    @Test
    fun `a backup of a newer format or a damaged file is refused with why`() =
        runTest {
            folders.opening = BackupOpening.NewerVersion(BACKUP.header.copy(formatVersion = 2))
            val viewModel = viewModel()
            val effects = effects(viewModel)

            send(viewModel, BackupEvent.RestoreFile(FILE), BackupEvent.OpenBackup("correct horse"))
            folders.opening = BackupOpening.NotABackup
            send(viewModel, BackupEvent.RestoreFile(FILE), BackupEvent.OpenBackup("correct horse"))

            assertEquals(
                listOf(
                    BackupEffect.RestoreRefused(RestoreRefusal.NewerVersion),
                    BackupEffect.RestoreRefused(RestoreRefusal.NotABackup),
                ),
                effects,
            )
            assertEquals(null, viewModel.restore())
        }

    @Test
    fun `restoring restores the opened backup and says another account's data stayed in it`() =
        runTest {
            backups.restoring = BackupRestored(settings = true, accounts = listOf(ADA), otherAccounts = listOf(OTHER))
            val viewModel = viewModel()
            val effects = effects(viewModel)

            send(
                viewModel,
                BackupEvent.RestoreFile(FILE),
                BackupEvent.OpenBackup("correct horse"),
                BackupEvent.ConfirmRestore,
            )

            assertEquals(listOf(BACKUP), backups.restored)
            assertEquals(listOf(BackupEffect.Restored(otherAccount = true)), effects)
            assertEquals(null, viewModel.restore())
        }

    @Test
    fun `a restore that fails says why, and leaving forgets the backup`() =
        runTest {
            backups.failure = DomainError.FileUnavailable
            val viewModel = viewModel()
            val effects = effects(viewModel)

            send(
                viewModel,
                BackupEvent.RestoreFile(FILE),
                BackupEvent.OpenBackup("correct horse"),
                BackupEvent.ConfirmRestore,
            )
            send(viewModel, BackupEvent.RestoreFile(FILE), BackupEvent.DismissRestore, BackupEvent.ConfirmRestore)

            assertEquals(listOf(BackupEffect.ShowError(DomainError.FileUnavailable)), effects)
            assertEquals(emptyList<Backup>(), backups.restored)
            assertEquals(null, viewModel.restore())
        }

    private fun user(
        id: Long,
        name: String,
    ) = User(
        id = id,
        firstName = name,
        lastName = "",
        usernames = persistentListOf(),
        phoneNumber = "",
        status = UserStatus.Empty,
        profilePhoto = null,
        isVerified = false,
        isScam = false,
        isFake = false,
        isPremium = false,
        isContact = false,
        isMutualContact = false,
        isSupport = false,
        type = UserType.Regular,
        haveAccess = true,
    )

    private companion object {
        const val FILE = "content://picked/backup"
        const val ADA = 7L
        const val OTHER = 8L
        val CREATED: Instant = Instant.parse("2026-11-12T09:00:00Z")
        val BACKUP =
            Backup(
                header = BackupHeader(1, "2.4.0", CREATED, setOf(BackupSection.Settings, BackupSection.Account)),
                settings = mapOf("stickers.loop" to false),
                accounts =
                    listOf(
                        AccountBackup(
                            ADA,
                            bookmarks = listOf(Bookmark(1, 2, savedAt = 3)),
                            snippets = listOf(Snippet(1, "a", "b")),
                        ),
                        AccountBackup(OTHER, snippets = listOf(Snippet(1, "c", "d"))),
                    ),
            )
    }
}
