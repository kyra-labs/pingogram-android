package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.focus.FocusToggle
import dev.kyralabs.pingogram.core.domain.focus.FocusToggleResult
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.domain.model.FocusProfile
import dev.kyralabs.pingogram.core.domain.model.FocusSchedule
import dev.kyralabs.pingogram.core.domain.model.FocusUntil
import dev.kyralabs.pingogram.core.domain.model.ManualFocus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** The Quick Settings tile's one tap (board 31): the last used profile on, or Focus off, on the real repository. */
class FocusToggleTest {
    @TempDir
    lateinit var directory: File

    private val zone = ZoneId.of("Asia/Kolkata")

    /** Saturday 2026-11-21, 11:00: no weekday schedule covers it. */
    private val now = LocalDateTime.of(2026, 11, 21, 11, 0).atZone(zone).toInstant()

    private val clock =
        object : WallClock {
            override fun now(): Instant = now

            override fun zone(): ZoneId = zone

            override val minutes: SharedFlow<Instant> = MutableSharedFlow()
        }

    private val work =
        FocusProfile(
            id = 0,
            name = "Work hours",
            icon = FocusIcon.Work,
            allowedFolderIds = persistentListOf(),
            allowedChatIds = persistentListOf(),
            schedule = FocusSchedule(DayOfWeek.entries.take(5).toImmutableSet(), 9 * 60, 18 * 60),
            allowsCallsFromContacts = true,
        )
    private val weekend = work.copy(name = "Weekend", icon = FocusIcon.Weekend, schedule = null)

    private fun TestScope.repository() =
        DataStoreFocusRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(directory, FILE) }),
        )

    @Test
    fun `without profiles there is nothing to turn on`() =
        runTest {
            assertEquals(FocusToggleResult.NoProfiles, FocusToggle(repository(), clock).toggle())
        }

    @Test
    fun `turns the first profile on before any was used, until the schedule's next change`() =
        runTest {
            val repository = repository()
            repository.saveProfile(work)
            repository.saveProfile(weekend)

            assertEquals(FocusToggleResult.Done(1L), FocusToggle(repository, clock).toggle())

            assertEquals(ManualFocus(1L, now.epochSecond, FocusUntil.ScheduleChange), repository.manual.first())
        }

    @Test
    fun `turns the last used profile on, then off`() =
        runTest {
            val repository = repository()
            repository.saveProfile(work)
            repository.saveProfile(weekend)
            repository.setManual(ManualFocus(2L, now.epochSecond - 3_600, FocusUntil.Time(now.epochSecond - 60)))
            val toggle = FocusToggle(repository, clock)

            assertEquals(FocusToggleResult.Done(2L), toggle.toggle())
            assertEquals(FocusToggleResult.Done(null), toggle.toggle())

            assertEquals(null, repository.manual.first()?.profileId)
            assertEquals(2L, repository.lastUsedId.first())
        }

    @Test
    fun `without schedules a profile on by hand stays on until turned off`() =
        runTest {
            val repository = repository()
            repository.saveProfile(weekend)

            FocusToggle(repository, clock).toggle()

            assertEquals(FocusUntil.TurnedOff, repository.manual.first()?.until)
        }

    private companion object {
        const val FILE = "settings.preferences_pb"
    }
}
