package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.domain.model.FocusProfile
import dev.kyralabs.pingogram.core.domain.model.FocusSchedule
import dev.kyralabs.pingogram.core.domain.model.FocusUntil
import dev.kyralabs.pingogram.core.domain.model.ManualFocus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.DayOfWeek

/** [DataStoreFocusRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreFocusRepositoryTest {
    @TempDir
    lateinit var directory: File

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(directory, FILE) })

    private val sleep =
        FocusProfile(
            id = 0,
            name = "Sleep | night, with a comma",
            icon = FocusIcon.Sleep,
            allowedFolderIds = persistentListOf(2, 5),
            allowedChatIds = persistentListOf(-100123L, 42L),
            schedule = FocusSchedule(persistentSetOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), 22 * 60 + 30, 7 * 60),
            allowsCallsFromContacts = true,
        )

    @Test
    fun `none until saved`() =
        runTest {
            val repository = DataStoreFocusRepository(dataStore())
            assertTrue(repository.profiles.first().isEmpty())
            assertNull(repository.manual.first())
            assertNull(repository.lastUsedId.first())
        }

    @Test
    fun `saves new profiles with their own ids, in order, and changes one in place`() =
        runTest {
            val repository = DataStoreFocusRepository(dataStore())

            assertEquals(Outcome.Success(1L), repository.saveProfile(sleep))
            assertEquals(Outcome.Success(2L), repository.saveProfile(sleep.copy(name = "Work", schedule = null)))
            repository.saveProfile(sleep.copy(id = 1, name = "Night"))

            val profiles = repository.profiles.first()
            assertEquals(listOf(1L, 2L), profiles.map { it.id })
            assertEquals(sleep.copy(id = 1, name = "Night"), profiles[0])
            assertNull(profiles[1].schedule)
        }

    @Test
    fun `keeps Focus set by hand and the last used, and deleting a profile ends it`() =
        runTest {
            val repository = DataStoreFocusRepository(dataStore())
            repository.saveProfile(sleep)
            val manual = ManualFocus(1, 1_700_000_000, FocusUntil.Time(1_700_003_600))

            repository.setManual(manual)
            assertEquals(manual, repository.manual.first())
            assertEquals(1L, repository.lastUsedId.first())
            repository.setManual(ManualFocus(null, 1_700_000_100, FocusUntil.ScheduleChange))
            assertEquals(1L, repository.lastUsedId.first())

            repository.setManual(manual)
            repository.deleteProfile(1)
            assertNull(repository.manual.first())
            assertNull(repository.lastUsedId.first())
            assertTrue(repository.profiles.first().isEmpty())
        }

    @Test
    fun `what cannot be read is left out`() {
        assertNull(decodeProfile("1|bad"))
        assertNull(decodeManual("x|y|z"))
        assertEquals(ManualFocus(null, 5, FocusUntil.TurnedOff), decodeManual("|5|off"))
    }

    private companion object {
        const val FILE = "settings.preferences_pb"
    }
}
