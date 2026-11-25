package dev.kyralabs.pingogram.feature.chats.ui.restore

import dev.kyralabs.pingogram.core.domain.model.BackupFileInfo
import dev.kyralabs.pingogram.core.domain.model.BackupFolder
import dev.kyralabs.pingogram.core.testing.FakeBackupFolderRepository
import dev.kyralabs.pingogram.core.testing.FakeBackupSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeDemoRepository
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
 * The chat list's restore card: the kept folder's newest backup, or after a sign-in the offer to restore from a file
 * while no folder is kept; "Not now" hiding it (for that backup, or until the next sign-in), "Restore" and a picked
 * file opening Backup & restore, and nothing in the demo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RestorePromptViewModelTest {
    private val folders = FakeBackupFolderRepository()
    private val settings = FakeBackupSettingsRepository()
    private val demo = FakeDemoRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): RestorePromptViewModel =
        RestorePromptViewModel(folders, settings, demo).also {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
            runCurrent()
        }

    private fun TestScope.effects(viewModel: RestorePromptViewModel): List<RestorePromptEffect> =
        mutableListOf<RestorePromptEffect>().also { list ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(list) }
        }

    private fun TestScope.send(
        viewModel: RestorePromptViewModel,
        event: RestorePromptEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    @Test
    fun `without a kept folder, a sign-in offers to restore from a file until hidden`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(RestorePromptUiState.Hidden, viewModel.uiState.value)

            settings.offerRestorePrompt()
            runCurrent()
            assertEquals(RestorePromptUiState.FromFile, viewModel.uiState.value)

            send(viewModel, RestorePromptEvent.NotNow)

            assertEquals(false, settings.restorePromptPending.value)
            assertEquals(RestorePromptUiState.Hidden, viewModel.uiState.value)
        }

    @Test
    fun `a picked file opens Backup & restore with it, and the offer goes`() =
        runTest {
            settings.offerRestorePrompt()
            val viewModel = viewModel()
            val effects = effects(viewModel)

            send(viewModel, RestorePromptEvent.ChooseFile)
            send(viewModel, RestorePromptEvent.FilePicked("content://picked/1"))

            assertEquals(
                listOf(RestorePromptEffect.PickFile, RestorePromptEffect.OpenBackup("content://picked/1")),
                effects,
            )
            assertEquals(RestorePromptUiState.Hidden, viewModel.uiState.value)
        }

    @Test
    fun `a kept folder offers its newest backup, until that one is hidden`() =
        runTest {
            folders.folder.value = BackupFolder(TREE, "Backups", persistentListOf(NEWER, OLDER))
            val viewModel = viewModel()
            assertEquals(RestorePromptUiState.FromFolder(NEWER.document, NEWER.modifiedAt), viewModel.uiState.value)

            send(viewModel, RestorePromptEvent.NotNow)
            assertEquals(RestorePromptUiState.Hidden, viewModel.uiState.value)

            val newest = NEWER.copy(document = "$TREE/3")
            folders.folder.value = BackupFolder(TREE, "Backups", persistentListOf(newest, NEWER))
            runCurrent()
            assertEquals(RestorePromptUiState.FromFolder(newest.document, newest.modifiedAt), viewModel.uiState.value)
        }

    @Test
    fun `restoring opens Backup & restore with the backup, which then shows no more`() =
        runTest {
            folders.folder.value = BackupFolder(TREE, "Backups", persistentListOf(NEWER))
            val viewModel = viewModel()
            val effects = effects(viewModel)

            send(viewModel, RestorePromptEvent.Restore)

            assertEquals(listOf(RestorePromptEffect.OpenBackup(NEWER.document)), effects)
            assertEquals(RestorePromptUiState.Hidden, viewModel.uiState.value)
        }

    @Test
    fun `a kept folder without backups, and the demo, show nothing`() =
        runTest {
            folders.folder.value = BackupFolder(TREE, "Backups", persistentListOf())
            val viewModel = viewModel()
            assertEquals(RestorePromptUiState.Hidden, viewModel.uiState.value)

            folders.folder.value = null
            demo.isDemo.value = true
            runCurrent()
            assertEquals(RestorePromptUiState.Hidden, viewModel.uiState.value)
        }

    private companion object {
        const val TREE = "content://tree/backups"
        val NEWER = BackupFileInfo("$TREE/2", "Pingogram backup 2026-11-12.pgbackup", Instant.ofEpochSecond(200), 4_096)
        val OLDER = BackupFileInfo("$TREE/1", "Pingogram backup 2026-11-05.pgbackup", Instant.ofEpochSecond(100), 2_048)
    }
}
