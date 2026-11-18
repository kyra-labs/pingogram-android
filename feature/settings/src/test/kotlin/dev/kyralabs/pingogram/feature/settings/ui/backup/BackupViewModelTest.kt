package dev.kyralabs.pingogram.feature.settings.ui.backup

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.BackupFileInfo
import dev.kyralabs.pingogram.core.domain.model.BackupFolder
import dev.kyralabs.pingogram.core.testing.FakeBackupFolderRepository
import dev.kyralabs.pingogram.core.testing.FakeBackupRepository
import dev.kyralabs.pingogram.core.testing.FakeDeviceAccountsRepository
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
 * The Backup & restore page: the kept folder and its newest backup, a picked folder kept, "Back up now" through the
 * password dialog (setting it at the folder's first backup), and what a backup that fails says.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val folders = FakeBackupFolderRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): BackupViewModel =
        BackupViewModel(folders, FakeBackupRepository(), FakeDeviceAccountsRepository()).also {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
            runCurrent()
        }

    private fun BackupViewModel.content() = uiState.value as BackupUiState.Content

    private fun TestScope.effects(viewModel: BackupViewModel): List<BackupEffect> =
        mutableListOf<BackupEffect>().also { list ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(list) }
        }

    @Test
    fun `without a folder, nothing can be backed up`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(BackupEvent.BackUp)
            runCurrent()

            assertEquals(BackupUiState.Content(), viewModel.content())
        }

    @Test
    fun `a picked folder is kept and shown with its newest backup`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(BackupEvent.FolderPicked(TREE))
            runCurrent()
            folders.folder.value = BackupFolder(TREE, "backups", listOf(NEWER, OLDER))
            runCurrent()

            assertEquals(BackupUiState.Content(folderName = "backups", lastBackup = NEWER), viewModel.content())
        }

    @Test
    fun `a folder that cannot be used says so`() =
        runTest {
            folders.failure = DomainError.FileUnavailable
            val viewModel = viewModel()
            val effects = effects(viewModel)

            viewModel.onEvent(BackupEvent.FolderPicked(TREE))
            runCurrent()

            assertEquals(listOf(BackupEffect.ShowError(DomainError.FileUnavailable)), effects)
        }

    @Test
    fun `the folder's first backup sets the password, later ones ask for it`() =
        runTest {
            folders.folder.value = BackupFolder(TREE, "backups", emptyList())
            val viewModel = viewModel()

            viewModel.onEvent(BackupEvent.BackUp)
            runCurrent()
            assertEquals(PasswordDialog.New, viewModel.content().password)

            viewModel.onEvent(BackupEvent.DismissPassword)
            folders.folder.value = BackupFolder(TREE, "backups", listOf(OLDER))
            runCurrent()
            viewModel.onEvent(BackupEvent.BackUp)
            runCurrent()
            assertEquals(PasswordDialog.Existing, viewModel.content().password)
        }

    @Test
    fun `backing up writes with the password typed, then says where`() =
        runTest {
            folders.folder.value = BackupFolder(TREE, "backups", emptyList())
            val viewModel = viewModel()
            val effects = effects(viewModel)

            viewModel.onEvent(BackupEvent.BackUp)
            viewModel.onEvent(BackupEvent.ConfirmPassword("correct horse"))
            runCurrent()

            assertEquals(listOf("correct horse"), folders.passwords)
            assertEquals(listOf(BackupEffect.BackedUp("backups")), effects)
            assertEquals(null, viewModel.content().password)
            assertEquals(FakeBackupFolderRepository.BACKUP_NAME, viewModel.content().lastBackup?.name)
        }

    @Test
    fun `a password shorter than six characters writes nothing`() =
        runTest {
            folders.folder.value = BackupFolder(TREE, "backups", emptyList())
            val viewModel = viewModel()

            viewModel.onEvent(BackupEvent.BackUp)
            viewModel.onEvent(BackupEvent.ConfirmPassword("short"))
            runCurrent()

            assertEquals(emptyList<String>(), folders.passwords)
            assertEquals(PasswordDialog.New, viewModel.content().password)
        }

    @Test
    fun `a backup that fails says why and can be tried again`() =
        runTest {
            folders.folder.value = BackupFolder(TREE, "backups", emptyList())
            val viewModel = viewModel()
            val effects = effects(viewModel)
            folders.failure = DomainError.FileUnavailable

            viewModel.onEvent(BackupEvent.BackUp)
            viewModel.onEvent(BackupEvent.ConfirmPassword("correct horse"))
            runCurrent()

            assertEquals(listOf(BackupEffect.ShowError(DomainError.FileUnavailable)), effects)
            assertEquals(BackupUiState.Content(folderName = "backups"), viewModel.content())
        }

    private companion object {
        const val TREE = "content://tree/backups"
        val NEWER = BackupFileInfo("$TREE/2", "Pingogram backup 2026-11-18.pgbackup", Instant.ofEpochSecond(200), 4_096)
        val OLDER = BackupFileInfo("$TREE/1", "Pingogram backup 2026-11-11.pgbackup", Instant.ofEpochSecond(100), 2_048)
    }
}
