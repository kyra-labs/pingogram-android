package dev.kyralabs.pingogram.core.notifications

import dev.kyralabs.pingogram.core.domain.model.BackupFrequency
import dev.kyralabs.pingogram.core.testing.FakeAutomaticBackupRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The automatic backups' schedule follows the user's choice and whether they can run. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticBackupsTest {
    private val backups = FakeAutomaticBackupRepository()
    private val work = RecordingWork()

    private fun TestScope.start() {
        AutomaticBackups(backups, work, backgroundScope).start()
        advanceTimeBy(AutomaticBackups.START_DELAY)
        runCurrent()
    }

    @Test
    fun `nothing is scheduled until they can run, then the choice is`() =
        runTest {
            backups.frequency.value = BackupFrequency.Weekly
            start()
            assertEquals(listOf("cancel"), work.calls)

            backups.isReady.value = true
            runCurrent()
            backups.frequency.value = BackupFrequency.Daily
            runCurrent()

            assertEquals(listOf("cancel", "schedule Weekly", "schedule Daily"), work.calls)
        }

    @Test
    fun `turning them off, or losing the folder or key, cancels them`() =
        runTest {
            backups.frequency.value = BackupFrequency.Weekly
            backups.isReady.value = true
            start()

            backups.frequency.value = BackupFrequency.Off
            runCurrent()
            backups.frequency.value = BackupFrequency.Weekly
            runCurrent()
            backups.isReady.value = false
            runCurrent()

            assertEquals(listOf("schedule Weekly", "cancel", "schedule Weekly", "cancel"), work.calls)
        }

    private class RecordingWork : AutomaticBackupWork {
        val calls = mutableListOf<String>()

        override fun schedule(frequency: BackupFrequency) {
            calls += "schedule $frequency"
        }

        override fun cancel() {
            calls += "cancel"
        }
    }
}
