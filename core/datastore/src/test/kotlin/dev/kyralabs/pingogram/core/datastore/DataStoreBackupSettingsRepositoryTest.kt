package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** [DataStoreBackupSettingsRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreBackupSettingsRepositoryTest {
    @TempDir
    lateinit var directory: File

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(directory, "settings.preferences_pb") })

    @Test
    fun `nothing is kept at first`() =
        runTest {
            val repository = DataStoreBackupSettingsRepository(dataStore())

            assertEquals(null, repository.folderUri.first())
            assertEquals(null, repository.restoreDismissedFor.first())
            assertEquals(false, repository.restorePromptDismissed.first())
        }

    @Test
    fun `the folder and what the user hid survive the process, and the folder can be let go`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            val first = DataStoreBackupSettingsRepository(dataStore(firstProcess))
            assertEquals(Outcome.Success(Unit), first.setFolderUri("content://tree/backups"))
            assertEquals(Outcome.Success(Unit), first.dismissRestore("content://tree/backups/1"))
            assertEquals(Outcome.Success(Unit), first.dismissRestorePrompt())
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            val again = DataStoreBackupSettingsRepository(dataStore())
            assertEquals("content://tree/backups", again.folderUri.first())
            assertEquals("content://tree/backups/1", again.restoreDismissedFor.first())
            assertEquals(true, again.restorePromptDismissed.first())
            again.setFolderUri(null)
            assertEquals(null, again.folderUri.first())
        }

    @Test
    fun `none of it ever goes into a backup`() {
        assertEquals(
            listOf(false, false, false),
            listOf(
                "backup.folder",
                "backup.restore_dismissed_for",
                "backup.restore_prompt_dismissed",
            ).map(::isBackedUp),
        )
    }
}
